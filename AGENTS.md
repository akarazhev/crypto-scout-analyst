# AGENTS.md

This document provides guidelines for agentic coding contributors to the crypto-scout-analyst module.

## Project Overview

Java 25 Maven microservice for real-time cryptocurrency market analysis. Consumes data from RabbitMQ Streams, applies technical analysis using jcryptolib indicators, and provides analytical capabilities. Built on ActiveJ for fully async I/O.

## Build, Test, and Lint Commands

### Build
```bash
mvn clean install
mvn -q -DskipTests install
```

### Run All Tests
```bash
mvn test
mvn -q test
```

### Run Single Test
```bash
mvn test -Dtest=DataServiceTest
mvn test -Dtest=StreamOffsetsRepositoryTest
mvn -q test -Dtest=DataServiceTest
```

### Run Tests with System Properties
```bash
mvn -q -Dpodman.compose.up.timeout.min=5 test
mvn -q -Dtest.db.jdbc.url=jdbc:postgresql://localhost:5432/crypto_scout test
```

### Clean
```bash
mvn clean
```

## Architecture

### Stream Processing Pipeline
```
RabbitMQ Stream → Consumer → BytesToPayloadTransformer → AnalystTransformer → DataService → Output
```

### Key Components
- **StreamService**: Orchestrates Bybit and CryptoScout stream services
- **CryptoScoutService**: Consumes from crypto-scout-stream with transformers
- **BybitStreamService**: Consumes from bybit-stream
- **DataService**: Processes payloads asynchronously
- **AnalystTransformer**: Stream transformer for preprocessing
- **StreamPublisher**: Output publisher

### Modules (ActiveJ)
- **CoreModule**: Reactor and executor
- **WebModule**: HTTP server and health endpoint
- **AnalystModule**: Analysis services wiring

## Code Style Guidelines

### File Structure
- MIT License header at top (23 lines)
- Package declaration on line 25
- One blank line before imports
- Imports organized: java.*, third-party, then static imports (each group separated by blank line)
- One blank line after imports
- Class/enum/interface declaration
- No trailing whitespace

### Imports
```java
import java.io.IOException;
import java.time.Duration;

import com.github.akarazhev.jcryptolib.stream.Payload;
import io.activej.reactor.nio.NioReactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.akarazhev.cryptoscout.config.Constants.AmqpConfig.AMQP_RABBITMQ_HOST;
```

### Naming Conventions
- **Classes**: PascalCase (e.g., `StreamService`, `AnalystTransformer`)
- **Methods**: camelCase starting with lowercase verb (e.g., `processAsync`, `transformPayload`)
- **Constants**: UPPER_SNAKE_CASE in nested static classes (e.g., `STREAM_NAME`, `BATCH_SIZE`)
- **Parameters and locals**: camelCase using `final var` (e.g., `final var payload`, `final var result`)
- **Test classes**: `<ClassName>Test` suffix (e.g., `DataServiceTest`)
- **Test methods**: `should<Subject><Action>` pattern (e.g., `shouldProcessPayloadSuccessfully`)

### Access Modifiers
- **Utility classes**: package-private with private constructor throwing `UnsupportedOperationException`
- **Nested constant classes**: `final static` with private constructor throwing `UnsupportedOperationException`
- **Factory methods**: `public static` named `create()`
- **Instance fields**: `private final` or `private volatile` for thread-safe lazy initialization
- **Static fields**: `private static final`
- **Methods**: `public`, `private`, or package-private as needed

### Type System
- Java 25 with `maven.compiler.release=25`
- Use `final var` for local variable type inference when type is obvious
- Explicit types when readability improves
- `Map<String, Object>` for JSON data structures
- `Payload<Map<String, Object>>` for stream data

### Error Handling
- **Unchecked exceptions**: Use `IllegalStateException` for invalid state/conditions
- **Resource not found**: `IllegalStateException` with descriptive message
- **Try-with-resources**: Always for `Connection`, `Statement`, `ResultSet`, `InputStream`, `OutputStream`
- **Exception parameters**: `final Exception e` or `final Exception ex`
- **Interrupt handling**: `Thread.currentThread().interrupt()` in catch blocks for `InterruptedException`
- **Logging exceptions**: Include message and exception: `LOGGER.error("Failed to process", e)`
- **Exception chaining**: Wrap with cause: `throw new IllegalStateException(msg, e)`

### Stream Processing Patterns
```java
// Transformer pattern
public final class AnalystTransformer extends AbstractStreamTransformer<StreamPayload, StreamPayload> {
    @Override
    protected StreamDataAcceptor<StreamPayload> onResumed(final StreamDataAcceptor<StreamPayload> output) {
        return in -> {
            try {
                final var result = process(in);
                output.accept(result);
            } catch (final Exception ex) {
                LOGGER.error("Processing failed", ex);
                output.accept(new StreamPayload(in.stream(), in.offset(), null));
            }
        };
    }
}
```

### Testing (JUnit 6/Jupiter)
- **Test classes**: Package-private, no modifiers (e.g., `final class DataServiceTest`)
- **Lifecycle methods**: `@BeforeAll static void setUp()`, `@AfterAll static void tearDown()`
- **Test methods**: `@Test void should...() throws Exception`
- **Assertions**: Import from `org.junit.jupiter.api.Assertions` using static imports
- **No test runners**: Use standard JUnit 5 patterns

### Logging
- **Logger field**: `private static final Logger LOGGER = LoggerFactory.getLogger(ClassName.class)`
- **Log levels**: `info()` for important events, `warn()` for recoverable issues, `error()` for failures
- **Messages**: Descriptive, include context (e.g., `"Processed {} records"`, `"Stream consumer failed"`)
- **Exceptions**: Pass as second parameter: `LOGGER.error("Description", exception)`

### Constants Organization
Group related constants in nested static classes:
```java
final class Constants {
    static final String PATH_SEPARATOR = "/";

    final static class AmqpConfig {
        static final String AMQP_RABBITMQ_HOST = System.getProperty("amqp.rabbitmq.host", "localhost");
        static final int AMQP_STREAM_PORT = Integer.parseInt(System.getProperty("amqp.stream.port", "5552"));
    }

    final static class StreamConfig {
        static final Duration TIMEOUT = Duration.ofMinutes(Long.getLong("stream.timeout.min", 3L));
    }
}
```

### Concurrency
- **Volatile fields**: For lazy-initialized singleton-style fields
- **Thread naming**: Provide names for background threads
- **Interruption**: Always restore interrupt status when catching `InterruptedException`
- **Daemon threads**: Set for background readers that shouldn't block JVM shutdown
- **Async processing**: Use `Promise` and `DataService.processAsync()` patterns

### Resource Management
- **Try-with-resources**: Required for all closeable resources (SQL, streams, connections)
- **Null checks**: Throw `IllegalStateException` for null resources
- **Timeout handling**: Throw `IllegalStateException` with descriptive message including timeout value
- **Stream lifecycle**: Properly close consumers, producers, and environments in `stop()`

### Code Organization
- **Static imports**: From project's `Constants` class heavily used
- **Method length**: Keep reasonable, extract private helpers if too long
- **Transformers**: Keep transformation logic pure and focused
- **DataService**: Delegate async processing to DataService

### System Properties
All configuration via system properties with defaults:
```java
static final String VALUE = System.getProperty("property.key", "defaultValue");
static final int PORT = Integer.parseInt(System.getProperty("port.key", "5552"));
static final Duration TIMEOUT = Duration.ofMinutes(Long.getLong("timeout.key", 3L));
```

## Key Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Java | 25 | Language |
| ActiveJ | 6.0-rc2 | Async I/O framework |
| jcryptolib | 0.0.4 | Streaming, analysis indicators |
| RabbitMQ Stream Client | 1.4.0 | Streams protocol |
| PostgreSQL | 42.7.9 | Database driver |
| HikariCP | 7.0.2 | Connection pooling |
| JUnit | 6.1.0-M1 | Testing |

## License

MIT License - See `LICENSE` file.
