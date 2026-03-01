package io.github.kafka.parallel.exception;

/**
 * Thrown by {@code @KafkaParallelListener} methods to signal that a record
 * is permanently unprocessable. The parallel consumer will <b>not</b> retry
 * this record.
 *
 * <p>
 * All other exceptions thrown from a listener method are treated as
 * transient failures and will be retried according to the parallel consumer's
 * retry policy.
 *
 * <p>
 * Example usage:
 * 
 * <pre>{@code
 * @KafkaParallelListener(topics = "orders")
 * public void process(ConsumerRecord<String, Order> record) {
 *     if (record.value().getAmount() < 0) {
 *         throw new TerminalProcessingException("Negative amount is not recoverable");
 *     }
 *     // ... process normally
 * }
 * }</pre>
 */
public class TerminalProcessingException extends RuntimeException {

    public TerminalProcessingException(String message) {
        super(message);
    }

    public TerminalProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
