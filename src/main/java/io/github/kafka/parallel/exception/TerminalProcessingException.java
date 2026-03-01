package io.github.kafka.parallel.exception;

/**
 * Throw this from your listener method to signal a terminal failure —
 * the record will be sent to the DLQ (if configured) and NOT retried.
 *
 * <p>For all other exceptions, the record will be retried with backoff
 * up to {@code maxRetries} times.
 *
 * <p>Example:
 * <pre>{@code
 * @KafkaParallelListener(topics = "my-topic", dlqTopic = "my-topic-dlq")
 * public void handle(ConsumerRecord<String, String> record) {
 *     if (!isValidPayload(record.value())) {
 *         throw new TerminalProcessingException("Invalid payload, sending to DLQ: " + record.value());
 *     }
 *     myService.process(record.value());
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
