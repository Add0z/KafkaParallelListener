package io.github.kafka.parallel.config;

import io.confluent.parallelconsumer.ParallelConsumerOptions;
import io.confluent.parallelconsumer.ParallelStreamProcessor;
import io.github.kafka.parallel.annotation.KafkaParallelListener;
import io.github.kafka.parallel.processor.KafkaParallelListenerProcessor;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;

@AutoConfiguration
@ConditionalOnClass({ParallelStreamProcessor.class, KafkaParallelListener.class})
@EnableConfigurationProperties(KafkaParallelProperties.class)
public class KafkaParallelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KafkaParallelListenerProcessor kafkaParallelListenerProcessor(
            KafkaParallelProperties properties,
            KafkaListenerContainerFactory<?> kafkaListenerContainerFactory,
            ConsumerFactory<Object, Object> consumerFactory,
            ProducerFactory<Object, Object> producerFactory
    ) {
        return new KafkaParallelListenerProcessor(properties, kafkaListenerContainerFactory, consumerFactory, producerFactory);
    }
}
