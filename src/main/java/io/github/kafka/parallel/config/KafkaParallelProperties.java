package io.github.kafka.parallel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Global defaults for all {@code @KafkaParallelListener} instances.
 * These are overridden by values set directly on the annotation.
 *
 * <p>Example {@code application.yml}:
 * <pre>
 * kafka:
 *   parallel:
 *     default-max-concurrency: 200
 *     default-max-retries: 5
 *     default-retry-delay-ms: 2000
 *     use-virtual-threads: true
 * </pre>
 */
@ConfigurationProperties(prefix = "kafka.parallel")
public class KafkaParallelProperties {

    /** Global default for maxConcurrency if not set on the annotation. */
    private int defaultMaxConcurrency = 100;

    /** Global default for maxRetries if not set on the annotation. */
    private int defaultMaxRetries = 10;

    /** Global default for retryDelayMs if not set on the annotation. */
    private long defaultRetryDelayMs = 1000;

    /** Enable virtual threads globally for all listeners. */
    private boolean useVirtualThreads = false;

    public int getDefaultMaxConcurrency() { return defaultMaxConcurrency; }
    public void setDefaultMaxConcurrency(int defaultMaxConcurrency) { this.defaultMaxConcurrency = defaultMaxConcurrency; }

    public int getDefaultMaxRetries() { return defaultMaxRetries; }
    public void setDefaultMaxRetries(int defaultMaxRetries) { this.defaultMaxRetries = defaultMaxRetries; }

    public long getDefaultRetryDelayMs() { return defaultRetryDelayMs; }
    public void setDefaultRetryDelayMs(long defaultRetryDelayMs) { this.defaultRetryDelayMs = defaultRetryDelayMs; }

    public boolean isUseVirtualThreads() { return useVirtualThreads; }
    public void setUseVirtualThreads(boolean useVirtualThreads) { this.useVirtualThreads = useVirtualThreads; }
}
