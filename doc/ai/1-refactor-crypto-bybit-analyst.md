# Issue 1: Refactor `CryptoBybitAnalyst` service

In this `crypto-scout-analyst` project we are going to consume streaming data from the `rabbitmq` server, perform
analysis and produce it as streaming data into the `rabbitmq` server by refactoring the `CryptoBybitAnalyst` service.

## Roles

Take the following roles:

- Expert java engineer.
- Expert technical writer.

## Conditions

- Use the best practices and design patterns.
- Use the current technology stack.
- Implementation must be production ready and to be optimized to process a lot of the data.
- Do not hallucinate.

## Tasks

- As the `expert java engineer` review the current `crypto-scout-analyst` project implementation and update
  `CryptoBybitAnalyst` by reading streaming data from the `rabbitmq` server keeping it in Datastream of `activej 6.0`.
- As the `expert java engineer` review the current `crypto-scout-analyst` project implementation and update
  `CryptoBybitAnalyst` by performing analysis and transforming it into new objects and then produce it as streaming data
  into the `rabbitmq` server. Use defined components in the `CryptoBybitAnalyst`: `Consumer`, `Producer`,
  `StreamOffsetsRepository`. Let's track offset in the database after processing.
- As the `expert java engineer` create stubs for analysis, that will consume the objects from the Datastream and perform
  analysis and then produce it as streaming data into the `rabbitmq` server.
- As the `expert java engineer` recheck your proposal and make sure that they are correct and haven't missed any
  important points.
- As the technical writer update the `README.md` and `doc/0.0.1/analyst-production-setup.md` files with your results.
- As the technical writer update the `1-refactor-crypto-bybit-analyst.md` file with your resolution.

---

## Resolution

### Summary

- **Refactored** `com.github.akarazhev.cryptoscout.analyst.StreamService` to process RabbitMQ Streams using an *
  *ActiveJ Datastream** pipeline and to publish analysis results back to RabbitMQ Streams.
- **Tracked offsets** in PostgreSQL via `StreamOffsetsRepository`, resuming from the saved offset on startup, and
  committing offsets after successful processing.
- **Added analysis stubs** that validate BYBIT payloads and transform them into BYBIT_TA payloads.
- **Updated docs**: `README.md` and `doc/0.0.1/analyst-production-setup.md` with architecture and setup.

### Implementation Details

- **Dependencies**
    - Added `io.activej:activej-datastream:${activej.version}` to `pom.xml` to use ActiveJ Datastream (6.0 series).

- **New stream components** in `src/main/java/com/github/akarazhev/cryptoscout/analyst/stream/`:
    - `RabbitMessageSupplier`: custom `AbstractStreamSupplier<StreamIn>` that enqueues messages received from RabbitMQ
      consumer.
    - `BytesToPayloadTransformer`: decodes message bytes into `Payload<Map<String,Object>>` using `JsonUtils`.
    - `BybitAnalysisTransformer`: analysis stub that passes through BYBIT events as `Provider.BYBIT_TA`; non-BYBIT
      events produce a commit-only signal.
    - `RabbitStreamPublisher`: `AbstractStreamConsumer<StreamPayload>` that publishes analyzed payloads to the
      destination stream and commits source offsets in DB.
    - DTOs: `StreamIn`, `StreamPayload` to carry `stream`, `offset`, and decoded payload.

- **Pipeline** (built in `CryptoBybitAnalyst.start()`):
    - `RabbitMessageSupplier` -> `BytesToPayloadTransformer` -> `BybitAnalysisTransformer` -> `RabbitStreamPublisher`.
    - RabbitMQ consumer (`Environment.consumerBuilder`) reads from `amqp.crypto.bybit.stream` and enqueues messages to
      the supplier.
    - RabbitMQ producer publishes to `amqp.crypto.bybit.ta.stream`.

- **Offset Management**
    - On subscription, load saved offset via `StreamOffsetsRepository.getOffset(stream)` and set
      `OffsetSpecification.offset(saved+1)` or `first()`.
    - After each processed message:
        - If analysis produced an output, publish and on confirmed publish, upsert offset to DB.
        - If analysis filtered the message, skip publish and upsert offset to DB (commit-only).

### Best Practices Applied

- **Backpressure**: publisher consumer suspends/resumes per message to ensure at-least-once with controlled flow.
- **At-least-once semantics**: offsets are committed only after successful publish or explicit skip decision.
- **Separation of concerns**: supplier/transformers/publisher are isolated, making analysis extendable and testable.
- **ActiveJ integration**: uses `StreamSupplier.transformWith(...)` and `AbstractStreamTransformer`/
  `AbstractStreamConsumer` following ActiveJ patterns.

### Files Changed

- `pom.xml` — added `activej-datastream` dependency.
- `CryptoBybitAnalyst.java` — built pipeline, wired RabbitMQ consumer/producer, offset resume, and graceful shutdown.
- `analyst/stream/*` — new supplier, transformers, publisher and DTOs.
- `README.md` — architecture, configuration, build/run.
- `doc/0.0.1/analyst-production-setup.md` — production setup for RabbitMQ Streams and PostgreSQL offsets table.

### Next Steps

- Extend `BybitAnalysisTransformer` with real analytics (feature engineering, indicators, signals).
- Tune RabbitMQ consumer credit and batching per throughput/SLA.
- Add metrics & tracing (JMX already present via modules).
