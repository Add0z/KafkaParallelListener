package io.github.kafka.parallel.example;

import io.github.kafka.parallel.annotation.KafkaParallelListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

@Component
public class ExampleListeners {

    @KafkaParallelListener(topics = "my-topic", concurrency = 4, ordering = KafkaParallelListener.Ordering.KEY)
    public void listen(ConsumerRecord<String, String> record) {
        System.out.println("Processing record: " + record.key() + " - " + record.value());
        try {
            Thread.sleep(100); // Simulate work
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @KafkaParallelListener(topics = "another-topic", concurrency = 8, ordering = KafkaParallelListener.Ordering.UNORDERED)
    public void listenUnordered(String message) {
        System.out.println("Processing unordered message: " + message);
    }
}
