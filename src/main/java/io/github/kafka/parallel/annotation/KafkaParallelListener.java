package io.github.kafka.parallel.annotation;

import io.confluent.parallelconsumer.ParallelConsumerOptions.ProcessingOrder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a parallel Kafka consumer powered by Confluent's Parallel Consumer.
 *
 * <p>Unlike {@code @KafkaListener}, this annotation processes messages concurrently
 * within a single consumer instance, bypassing the partition-count limitation.
 *
 * <p>Example usage:
 * <pre>{@code
 * @KafkaParallelListener(
 *     topics = "my-topic",
 *     groupId = "my-group",
 *     maxConcurrency = 200,
 *     ordering = ProcessingOrder.KEY
 * )
 * public void handle(ConsumerRecord<String, String> record) {
 *     myService.process(record.value());
 * }
 * }</pre>
 *
 * <p>The annotated method must accept a single {@code ConsumerRecord<String, String>} parameter.
 * Throwing any exception from the method will trigger a retry with backoff.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KafkaParallelListener {

    /**
     * Topics to subscribe to.
     */
    String[] topics();

    /**
     * Kafka consumer group ID.
     * If empty, falls back to spring.kafka.consumer.group-id property.
     */
    String groupId() default "";

    /**
     * Maximum number of messages processed concurrently across all assigned partitions.
     */
    int maxConcurrency() default 100;

    /**
     * Message processing order guarantee.
     * KEY = per-key ordering (recommended), PARTITION = per-partition, UNORDERED = maximum throughput.
     */
    ProcessingOrder ordering() default ProcessingOrder.KEY;

    /**
     * Use virtual threads (Project Loom) for the worker pool.
     * Requires Java 21+ and parallel-consumer built with PR#908.
     */
    boolean useVirtualThreads() default false;

    /**
     * Initial retry delay in milliseconds when message processing fails.
     */
    long retryDelayMs() default 1000;

    /**
     * Maximum number of retry attempts before the record is sent to the DLQ.
     * Set to -1 for infinite retries.
     */
    int maxRetries() default 10;

    /**
     * Dead letter queue topic name. If empty, failed messages are just logged and skipped.
     */
    String dlqTopic() default "";

    /**
     * Batch size. If > 1, the method must accept {@code List<ConsumerRecord<String, String>>}.
     */
    int batchSize() default 1;
}
