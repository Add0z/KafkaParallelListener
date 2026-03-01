package io.github.kafka.parallel.processor;

import io.confluent.parallelconsumer.ParallelConsumerOptions;
import io.confluent.parallelconsumer.ParallelConsumerOptions.ProcessingOrder;
import io.confluent.parallelconsumer.ParallelStreamProcessor;
import io.github.kafka.parallel.annotation.KafkaParallelListener;
import io.github.kafka.parallel.config.KafkaParallelProperties;
import io.github.kafka.parallel.exception.TerminalProcessingException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring {@link BeanPostProcessor} that scans all beans for methods annotated
 * with {@link KafkaParallelListener} and wires up a {@link ParallelStreamProcessor}
 * for each one.
 *
 * <p>Registered automatically via Spring Boot autoconfiguration.
 */
public class KafkaParallelListenerProcessor implements BeanPostProcessor, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(KafkaParallelListenerProcessor.class);

    private final ConsumerFactory<String, String> consumerFactory;
    private final ProducerFactory<String, String> producerFactory;
    private final KafkaParallelProperties properties;

    @Value("${spring.kafka.consumer.group-id:default-parallel-group}")
    private String defaultGroupId;

    /** All active processors, keyed by "beanName#methodName" for observability. */
    private final Map<String, ParallelStreamProcessor<String, String>> processors = new ConcurrentHashMap<>();

    public KafkaParallelListenerProcessor(
            ConsumerFactory<String, String> consumerFactory,
            ProducerFactory<String, String> producerFactory,
            KafkaParallelProperties properties) {
        this.consumerFactory = consumerFactory;
        this.producerFactory = producerFactory;
        this.properties = properties;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();

        for (Method method : targetClass.getDeclaredMethods()) {
            KafkaParallelListener annotation = method.getAnnotation(KafkaParallelListener.class);
            if (annotation == null) continue;

            validateMethod(method, annotation);
            String processorKey = beanName + "#" + method.getName();

            log.info("[KafkaParallelListener] Registering listener: {} → topics={}, concurrency={}, ordering={}",
                    processorKey,
                    List.of(annotation.topics()),
                    annotation.maxConcurrency(),
                    annotation.ordering());

            try {
                ParallelStreamProcessor<String, String> pc = createProcessor(bean, method, annotation);
                processors.put(processorKey, pc);
            } catch (Exception e) {
                throw new BeanInitializationException(
                        "Failed to start KafkaParallelListener on " + processorKey, e);
            }
        }
        return bean;
    }

    private ParallelStreamProcessor<String, String> createProcessor(
            Object bean, Method method, KafkaParallelListener annotation) {

        String groupId = resolveGroupId(annotation);
        Consumer<String, String> consumer = createConsumer(groupId);

        boolean useDlq = StringUtils.hasText(annotation.dlqTopic());
        Producer<String, String> producer = useDlq ? producerFactory.createProducer() : null;

        var optionsBuilder = ParallelConsumerOptions.<String, String>builder()
                .ordering(annotation.ordering())
                .maxConcurrency(annotation.maxConcurrency())
                .consumer(consumer)
                .retryDelayProvider(ctx -> Duration.ofMillis(
                        annotation.retryDelayMs() * (long) Math.pow(2, Math.min(ctx.getNumberOfFailedAttempts(), 5))
                ));

        if (producer != null) {
            optionsBuilder.producer(producer);
        }

        // Uncomment when PR#908 is merged:
        // if (annotation.useVirtualThreads() || properties.isUseVirtualThreads()) {
        //     optionsBuilder.useVirtualThreads(true);
        // }

        ParallelStreamProcessor<String, String> pc =
                ParallelStreamProcessor.createEosStreamProcessor(optionsBuilder.build());

        pc.subscribe(List.of(annotation.topics()));

        // Track retries per record
        Map<String, Integer> retryCount = new ConcurrentHashMap<>();

        pc.poll(context -> {
            ConsumerRecord<String, String> record = context.getSingleRecord().getConsumerRecord();
            String recordKey = recordKey(record);

            try {
                method.setAccessible(true);
                method.invoke(bean, record);
                retryCount.remove(recordKey); // success — clean up

            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;

                // Terminal failure — send to DLQ and skip
                if (cause instanceof TerminalProcessingException) {
                    log.error("[KafkaParallelListener] Terminal failure on record {}:{} topic={} — {}",
                            record.key(), record.offset(), record.topic(), cause.getMessage());
                    sendToDlq(producer, annotation.dlqTopic(), record, cause);
                    retryCount.remove(recordKey);
                    return; // don't rethrow — marks record as complete
                }

                // Max retries exceeded — send to DLQ and skip
                int maxRetries = annotation.maxRetries();
                if (maxRetries >= 0) {
                    int attempts = retryCount.merge(recordKey, 1, Integer::sum);
                    if (attempts > maxRetries) {
                        log.error("[KafkaParallelListener] Max retries ({}) exceeded for record {}:{} topic={} — giving up",
                                maxRetries, record.key(), record.offset(), record.topic(), cause);
                        sendToDlq(producer, annotation.dlqTopic(), record, cause);
                        retryCount.remove(recordKey);
                        return; // don't rethrow — marks record as complete
                    }
                    log.warn("[KafkaParallelListener] Retry {}/{} for record {}:{} topic={} — {}",
                            attempts, maxRetries, record.key(), record.offset(), record.topic(), cause.getMessage());
                }

                // Rethrow to trigger parallel consumer retry with backoff
                throw new RuntimeException(cause);
            }
        });

        return pc;
    }

    private void sendToDlq(Producer<String, String> producer, String dlqTopic,
                            ConsumerRecord<String, String> record, Throwable cause) {
        if (producer == null || !StringUtils.hasText(dlqTopic)) {
            log.warn("[KafkaParallelListener] No DLQ configured, dropping record {}:{} topic={}",
                    record.key(), record.offset(), record.topic());
            return;
        }
        try {
            var dlqRecord = new ProducerRecord<>(dlqTopic, record.key(), record.value());
            dlqRecord.headers().add("original-topic", record.topic().getBytes());
            dlqRecord.headers().add("original-offset", String.valueOf(record.offset()).getBytes());
            dlqRecord.headers().add("failure-reason", cause.getMessage() != null
                    ? cause.getMessage().getBytes() : "unknown".getBytes());
            producer.send(dlqRecord).get();
            log.info("[KafkaParallelListener] Record sent to DLQ {} — key={}", dlqTopic, record.key());
        } catch (Exception e) {
            log.error("[KafkaParallelListener] Failed to send record to DLQ {}", dlqTopic, e);
        }
    }

    private Consumer<String, String> createConsumer(String groupId) {
        Map<String, Object> props = new HashMap<>(consumerFactory.getConfigurationProperties());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"); // required by parallel consumer
        return new KafkaConsumer<>(props);
    }

    private String resolveGroupId(KafkaParallelListener annotation) {
        return StringUtils.hasText(annotation.groupId()) ? annotation.groupId() : defaultGroupId;
    }

    private void validateMethod(Method method, KafkaParallelListener annotation) {
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 1 || !ConsumerRecord.class.isAssignableFrom(params[0])) {
            throw new BeanInitializationException(
                    "@KafkaParallelListener method '" + method.getName() +
                    "' must have exactly one parameter of type ConsumerRecord<String, String>");
        }
        if (annotation.topics().length == 0) {
            throw new BeanInitializationException(
                    "@KafkaParallelListener on '" + method.getName() + "' must specify at least one topic");
        }
    }

    private String recordKey(ConsumerRecord<String, String> record) {
        return record.topic() + "-" + record.partition() + "-" + record.offset();
    }

    /** Returns an unmodifiable view of active processors for monitoring/testing. */
    public Map<String, ParallelStreamProcessor<String, String>> getProcessors() {
        return Map.copyOf(processors);
    }

    @Override
    public void destroy() {
        log.info("[KafkaParallelListener] Shutting down {} parallel consumer(s)...", processors.size());
        processors.forEach((key, pc) -> {
            try {
                pc.close();
                log.info("[KafkaParallelListener] Closed: {}", key);
            } catch (Exception e) {
                log.warn("[KafkaParallelListener] Error closing {}: {}", key, e.getMessage());
            }
        });
        processors.clear();
    }
}
