# @KafkaParallelListener

A Spring Boot annotation that brings Confluent's Parallel Consumer power to a familiar `@KafkaListener`-style API.

## The Problem

Standard `@KafkaListener` is limited by partition count:
- 3 partitions = max 3 concurrent messages processing at once
- Need more concurrency? Add more partitions (expensive, complex)

## The Solution

`@KafkaParallelListener` processes messages concurrently **client-side**:
- 1 partition = up to 1000 concurrent messages
- No broker changes needed
- Per-key ordering maintained

## Quick Start

### 1. Clone and install the library locally

```bash
git clone https://github.com/Add0z/KafkaParallelListener
cd KafkaParallelListener
mvn install -DskipTests
```

> This also installs the bundled `parallel-consumer-core` fork (built from [PR#908](https://github.com/confluentinc/parallel-consumer/pull/908) with Java 21 fixes) into your local Maven repository automatically.

### 2. Add to your project's `pom.xml`

```xml
<dependency>
    <groupId>io.github.kafka.parallel</groupId>
    <artifactId>kafka-parallel-listener</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 3. Configure `application.yml`

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: my-app-group
      enable-auto-commit: false   # required!

kafka:
  parallel:
    default-max-concurrency: 100
```

### 4. Use the annotation

```java
@Component
public class MyConsumer {

    // Simple usage
    @KafkaParallelListener(topics = "orders", groupId = "order-processor")
    public void processOrder(ConsumerRecord<String, String> record) {
        orderService.save(record.value()); // blocking calls are fine!
    }

    // Full configuration
    @KafkaParallelListener(
        topics = "payments",
        groupId = "payment-processor",
        maxConcurrency = 500,
        ordering = ProcessingOrder.KEY,
        maxRetries = 5,
        retryDelayMs = 2000,
        dlqTopic = "payments-dlq"
    )
    public void processPayment(ConsumerRecord<String, String> record) {
        paymentService.process(record.value());
    }
}
```

No additional configuration needed — Spring Boot autoconfiguration wires everything up automatically.

## Annotation Options

| Option | Default | Description |
|--------|---------|-------------|
| `topics` | required | Topics to subscribe to |
| `groupId` | spring.kafka.consumer.group-id | Consumer group ID |
| `maxConcurrency` | 100 | Max concurrent messages across all partitions |
| `ordering` | KEY | KEY / PARTITION / UNORDERED |
| `maxRetries` | 10 | Max retries before DLQ. -1 = infinite |
| `retryDelayMs` | 1000 | Base retry delay (exponential backoff applied) |
| `dlqTopic` | "" | Dead letter queue topic. Empty = log and skip |
| `useVirtualThreads` | false | Enable when PR#908 is merged |

## Retry & Error Handling

- **Retriable error**: throw any exception → retried with exponential backoff
- **Terminal error**: throw `TerminalProcessingException` → sent to DLQ immediately, no retry
- **Max retries exceeded**: sent to DLQ automatically

```java
@KafkaParallelListener(topics = "orders", dlqTopic = "orders-dlq", maxRetries = 3)
public void process(ConsumerRecord<String, String> record) {
    if (!isValid(record.value())) {
        throw new TerminalProcessingException("Bad payload"); // → DLQ, no retry
    }
    externalService.call(record.value()); // IOException → retry 3 times then DLQ
}
```

## Concurrency Model

```
1 partition, maxConcurrency=100, KEY ordering:

Partition 0:
  key="user-1" → msg1 ──► processing
  key="user-1" → msg2 ──► waiting (ordered after msg1)
  key="user-2" → msg3 ──► processing (different key = parallel)
  key="user-3" → msg4 ──► processing (different key = parallel)
  ...up to 100 concurrent messages
```

## Virtual Threads (coming soon)

When [PR#908](https://github.com/confluentinc/parallel-consumer/pull/908) is merged:

```yaml
kafka:
  parallel:
    use-virtual-threads: true  # enables Project Loom virtual threads
```

This removes the thread pool overhead entirely — each message gets its own virtual thread,
making blocking I/O essentially free.

## Dependencies

- Java 21+
- Spring Boot 3.2+
- Spring Kafka
- [Confluent Parallel Consumer PR#908 fork](https://github.com/Add0z/parallel-consumer/tree/features/enable-virtual-threads) (bundled in `libs/`)