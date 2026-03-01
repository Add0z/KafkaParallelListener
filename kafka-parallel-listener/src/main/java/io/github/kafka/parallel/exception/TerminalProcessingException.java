package io.github.kafka.parallel.exception;

public class TerminalProcessingException extends RuntimeException {
    public TerminalProcessingException(String message) {
        super(message);
    }

    public TerminalProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
