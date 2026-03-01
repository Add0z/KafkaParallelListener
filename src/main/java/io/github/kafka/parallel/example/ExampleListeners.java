package io.github.kafka.parallel.example;

import io.confluent.parallelconsumer.ParallelConsumerOptions.ProcessingOrder;
import io.github.kafka.parallel.annotation.KafkaParallelListener;
import io.github.kafka.parallel.exception.TerminalProcessingException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Example showing all features of @KafkaParallelListener.
 * This is NOT part of the library — copy what you need into your own project.
 */
@Component
public class ExampleListeners {

    private static final Logger log = LoggerFactory.getLogger(ExampleListeners.class);

    // -------------------------------------------------------------------------
    // 1. Simplest usage — just like @KafkaListener but parallel
    // -------------------------------------------------------------------------
    @KafkaParallelListener(
            topics = "orders",
            groupId = "order-processor"
    )
    public void processOrder(ConsumerRecord<String, String> record) {
        log.info("Processing order: key={} value={}", record.key(), record.value());
        // blocking calls are fine here — parallel consumer handles concurrency
        orderService.save(record.value());
    }

    // -------------------------------------------------------------------------
    // 2. High concurrency with key ordering — great for per-user operations
    // -------------------------------------------------------------------------
    @KafkaParallelListener(
            topics = "user-events",
            groupId = "user-event-processor",
            maxConcurrency = 500,
            ordering = ProcessingOrder.KEY  // events for same user are ordered
    )
    public void processUserEvent(ConsumerRecord<String, String> record) {
        userEventService.handle(record.key(), record.value());
    }

    // -------------------------------------------------------------------------
    // 3. With DLQ and retry configuration
    // -------------------------------------------------------------------------
    @KafkaParallelListener(
            topics = "payments",
            groupId = "payment-processor",
            maxConcurrency = 200,
            ordering = ProcessingOrder.KEY,
            maxRetries = 5,
            retryDelayMs = 2000,     // exponential backoff: 2s, 4s, 8s, 16s, 32s
            dlqTopic = "payments-dlq"
    )
    public void processPayment(ConsumerRecord<String, String> record) {
        try {
            paymentService.process(record.value());
        } catch (InvalidPaymentException e) {
            // Terminal — don't retry, go straight to DLQ
            throw new TerminalProcessingException("Invalid payment payload: " + record.key(), e);
        }
        // Any other exception triggers retry with backoff up to maxRetries times
    }

    // -------------------------------------------------------------------------
    // 4. Maximum throughput — no ordering needed (e.g. analytics events)
    // -------------------------------------------------------------------------
    @KafkaParallelListener(
            topics = "analytics",
            groupId = "analytics-processor",
            maxConcurrency = 1000,
            ordering = ProcessingOrder.UNORDERED
    )
    public void processAnalytics(ConsumerRecord<String, String> record) {
        analyticsService.ingest(record.value());
    }

    // -------------------------------------------------------------------------
    // 5. Multiple topics on one listener
    // -------------------------------------------------------------------------
    @KafkaParallelListener(
            topics = {"topic-a", "topic-b", "topic-c"},
            groupId = "multi-topic-processor",
            maxConcurrency = 300
    )
    public void processMultiTopic(ConsumerRecord<String, String> record) {
        log.info("Received from topic={} key={}", record.topic(), record.key());
        routingService.route(record.topic(), record.value());
    }

    // Placeholder services for compilation — replace with real injected services
    private final OrderService orderService = new OrderService();
    private final UserEventService userEventService = new UserEventService();
    private final PaymentService paymentService = new PaymentService();
    private final AnalyticsService analyticsService = new AnalyticsService();
    private final RoutingService routingService = new RoutingService();

    static class OrderService { void save(String v) {} }
    static class UserEventService { void handle(String k, String v) {} }
    static class PaymentService { void process(String v) throws InvalidPaymentException {} }
    static class AnalyticsService { void ingest(String v) {} }
    static class RoutingService { void route(String t, String v) {} }
    static class InvalidPaymentException extends Exception {}
}
