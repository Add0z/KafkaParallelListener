package io.github.kafka.parallel.processor;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.lang.NonNull;
import org.springframework.util.ReflectionUtils;

import io.confluent.parallelconsumer.ParallelConsumerOptions;
import io.confluent.parallelconsumer.ParallelStreamProcessor;
import io.github.kafka.parallel.annotation.KafkaParallelListener;
import io.github.kafka.parallel.config.KafkaParallelProperties;

public class KafkaParallelListenerProcessor implements BeanPostProcessor, ApplicationContextAware {

    private final KafkaParallelProperties properties;
    private final ConsumerFactory<Object, Object> consumerFactory;
    private final ProducerFactory<Object, Object> producerFactory;

    // TODO: use for proper listener container lifecycle management (start, stop, pause on rebalance)
    private final KafkaListenerContainerFactory<?> kafkaListenerContainerFactory;

    // TODO: use for resolving SpEL expressions and ${...} property placeholders in annotation attributes
    private ApplicationContext applicationContext;

    public KafkaParallelListenerProcessor(KafkaParallelProperties properties,
            KafkaListenerContainerFactory<?> kafkaListenerContainerFactory,
            ConsumerFactory<Object, Object> consumerFactory,
            ProducerFactory<Object, Object> producerFactory) {
        this.properties = properties;
        this.kafkaListenerContainerFactory = kafkaListenerContainerFactory;
        this.consumerFactory = consumerFactory;
        this.producerFactory = producerFactory;
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();
        ReflectionUtils.doWithMethods(targetClass, method -> {
            KafkaParallelListener annotation = AnnotationUtils.findAnnotation(method, KafkaParallelListener.class);
            if (annotation != null) {
                validateDeserializerCompatibility(method, consumerFactory);
                processListener(bean, method, annotation);
            }
        });
        return bean;
    }

    private void validateDeserializerCompatibility(Method method, ConsumerFactory<?, ?> consumerFactory) {
        Type genericType = method.getGenericParameterTypes().length > 0
                ? method.getGenericParameterTypes()[0]
                : null;

        if (!(genericType instanceof ParameterizedType pt)) return;

        // extrai o V de ConsumerRecord<K, V>
        Type[] typeArgs = pt.getActualTypeArguments();
        if (typeArgs.length < 2) return;

        Class<?> valueType = (Class<?>) typeArgs[1];

        String valueDeserializer = String.valueOf(
                consumerFactory.getConfigurationProperties()
                        .get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));

        if (valueDeserializer.contains("Avro") && !isSpecificAvroReaderEnabled(consumerFactory)) {
            throw new BeanCreationException(
                    "[@KafkaParallelListener] Method '" + method.getName() + "' expects value type '"
                    + valueType.getSimpleName() + "' but KafkaAvroDeserializer is configured without "
                    + "'specific.avro.reader=true'. The deserializer will return GenericRecord instead of "
                    + valueType.getSimpleName() + ", causing a ClassCastException at runtime. "
                    + "Add 'specific.avro.reader: true' to your consumer properties.");
        }
    }

    private boolean isSpecificAvroReaderEnabled(ConsumerFactory<?, ?> consumerFactory) {
        Object value = consumerFactory.getConfigurationProperties()
                .get("specific.avro.reader");
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private void processListener(Object bean, Method method, KafkaParallelListener annotation) {
        // 1. Create Kafka Consumer
        Consumer<Object, Object> consumer = consumerFactory.createConsumer();
        consumer.subscribe(Collections.singletonList(annotation.topics()[0]));

        // 2. Create Parallel Consumer Options
        ParallelConsumerOptions.ProcessingOrder order = convertOrder(annotation.ordering());
        int concurrency = annotation.concurrency() > 0 ? annotation.concurrency() : properties.getDefaultConcurrency();

        ParallelConsumerOptions<Object, Object> options = ParallelConsumerOptions.<Object, Object>builder()
                .ordering(order)
                .consumer(consumer)
                .producer(producerFactory.createProducer())
                .maxConcurrency(concurrency)
                .build();

        // 3. Create Parallel Stream Processor
        ParallelStreamProcessor<Object, Object> processor = ParallelStreamProcessor.createEosStreamProcessor(options);

        // 4. Start Processing
        processor.poll(context -> {
            try {
                method.invoke(bean, context.getSingleRecord().getConsumerRecord());
            } catch (Exception e) {
                throw new RuntimeException("Error invoking listener method", e);
            }
        });
    }

    private ParallelConsumerOptions.ProcessingOrder convertOrder(KafkaParallelListener.Ordering ordering) {
        return switch (ordering) {
            case PARTITION -> ParallelConsumerOptions.ProcessingOrder.PARTITION;
            case UNORDERED -> ParallelConsumerOptions.ProcessingOrder.UNORDERED;
            default -> ParallelConsumerOptions.ProcessingOrder.KEY;
        };
    }
}