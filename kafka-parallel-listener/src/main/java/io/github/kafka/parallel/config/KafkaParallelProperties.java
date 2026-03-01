package io.github.kafka.parallel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka.parallel")
public class KafkaParallelProperties {

    /**
     * Default concurrency level for parallel consumers.
     * Can be overridden per listener.
     */
    private int defaultConcurrency = 16;

    /**
     * Default ordering guarantee.
     * Can be overridden per listener.
     */
    private String defaultOrdering = "KEY";

    /**
     * Whether to commit offsets synchronously or asynchronously.
     */
    private boolean commitAsync = true;

    /**
     * Maximum number of records to buffer in memory.
     */
    private int maxBufferedRecords = 1000;

    public int getDefaultConcurrency() {
        return defaultConcurrency;
    }

    public void setDefaultConcurrency(int defaultConcurrency) {
        this.defaultConcurrency = defaultConcurrency;
    }

    public String getDefaultOrdering() {
        return defaultOrdering;
    }

    public void setDefaultOrdering(String defaultOrdering) {
        this.defaultOrdering = defaultOrdering;
    }

    public boolean isCommitAsync() {
        return commitAsync;
    }

    public void setCommitAsync(boolean commitAsync) {
        this.commitAsync = commitAsync;
    }

    public int getMaxBufferedRecords() {
        return maxBufferedRecords;
    }

    public void setMaxBufferedRecords(int maxBufferedRecords) {
        this.maxBufferedRecords = maxBufferedRecords;
    }
}
