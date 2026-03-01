package io.github.kafka.parallel.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.kafka.annotation.KafkaListener;

import java.lang.annotation.*;

/**
 * Annotation that marks a method to be the target of a Kafka message listener on the specified topics,
 * processed in parallel using Confluent's Parallel Consumer.
 * <p>
 * This annotation is a wrapper around {@link KafkaListener} but changes the processing model
 * to use {@code ParallelStreamProcessor}.
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@KafkaListener
public @interface KafkaParallelListener {

    /**
     * The unique identifier of the container managing for this endpoint.
     * <p>If none is specified an auto-generated one is provided.
     * @see KafkaListener#id()
     */
    @AliasFor(annotation = KafkaListener.class, attribute = "id")
    String id() default "";

    /**
     * The bean name of the {@link org.springframework.kafka.config.KafkaListenerContainerFactory}
     * to use to create the message listener container responsible for serving this endpoint.
     * <p>If not specified, the default container factory will be used.
     * @see KafkaListener#containerFactory()
     */
    @AliasFor(annotation = KafkaListener.class, attribute = "containerFactory")
    String containerFactory() default "";

    /**
     * The topics for this listener.
     * The entries can be 'topic name', 'property-placeholder keys' or 'expressions'.
     * @see KafkaListener#topics()
     */
    @AliasFor(annotation = KafkaListener.class, attribute = "topics")
    String[] topics() default {};

    /**
     * The topic pattern for this listener.
     * The entries can be 'topic pattern', 'property-placeholder keys' or 'expressions'.
     * @see KafkaListener#topicPattern()
     */
    @AliasFor(annotation = KafkaListener.class, attribute = "topicPattern")
    String topicPattern() default "";

    /**
     * The consumer group id.
     * @see KafkaListener#groupId()
     */
    @AliasFor(annotation = KafkaListener.class, attribute = "groupId")
    String groupId() default "";

    /**
     * Concurrency level for the Parallel Consumer.
     * <p>
     * This is NOT the number of threads in the Kafka Consumer (which is usually 1 per partition),
     * but the number of threads available for parallel processing of records.
     * <p>
     * Default is -1, which means "use the global default from properties".
     */
    int concurrency() default -1;

    /**
     * Ordering guarantee for the Parallel Consumer.
     * <p>
     * Options: KEY, PARTITION, UNORDERED.
     * Default is KEY (process records with the same key sequentially).
     */
    Ordering ordering() default Ordering.KEY;

    enum Ordering {
        KEY,
        PARTITION,
        UNORDERED
    }
}
