package io.github.kafka.parallel.config;

import io.github.kafka.parallel.processor.KafkaParallelListenerProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Spring Boot autoconfiguration for {@code @KafkaParallelListener}.
 *
 * <p>Automatically activated when {@code parallel-consumer-core} is on the classpath.
 * No manual {@code @EnableKafkaParallelListener} needed.
 *
 * <p>To disable: {@code kafka.parallel.enabled=false}
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(name = "io.confluent.parallelconsumer.ParallelStreamProcessor")
@EnableConfigurationProperties(KafkaParallelProperties.class)
public class KafkaParallelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KafkaParallelListenerProcessor kafkaParallelListenerProcessor(
            ConsumerFactory<String, String> consumerFactory,
            ProducerFactory<String, String> producerFactory,
            KafkaParallelProperties properties) {
        return new KafkaParallelListenerProcessor(consumerFactory, producerFactory, properties);
    }
}
