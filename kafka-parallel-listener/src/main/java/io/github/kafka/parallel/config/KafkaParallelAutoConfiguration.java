package io.github.kafka.parallel.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;

import io.confluent.parallelconsumer.ParallelStreamProcessor;
import io.github.kafka.parallel.processor.KafkaParallelListenerProcessor;

/**
 * Auto-configuration for {@code @KafkaParallelListener}.
 *
 * <p>
 * Activation conditions (ALL must be met):
 * <ul>
 * <li>{@code spring-kafka} is on the classpath ({@link ConsumerFactory})</li>
 * <li>{@code parallel-consumer-core} is on the classpath
 * ({@link ParallelStreamProcessor})</li>
 * <li>A {@link ConsumerFactory} bean exists (Kafka is actually configured)</li>
 * <li>The property {@code kafka.parallel.enabled} is not explicitly set to
 * {@code false}</li>
 * </ul>
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass({ ConsumerFactory.class, ParallelStreamProcessor.class })
@ConditionalOnBean(ConsumerFactory.class)
@ConditionalOnProperty(name = "kafka.parallel.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KafkaParallelProperties.class)
public class KafkaParallelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KafkaParallelListenerProcessor kafkaParallelListenerProcessor(
            KafkaParallelProperties properties,
            KafkaListenerContainerFactory<?> kafkaListenerContainerFactory,
            ConsumerFactory<Object, Object> consumerFactory,
            ProducerFactory<Object, Object> producerFactory) {
        return new KafkaParallelListenerProcessor(properties, kafkaListenerContainerFactory, consumerFactory,
                producerFactory);
    }
}
