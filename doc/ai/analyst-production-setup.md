# Analyst Production Setup

This document describes the production setup for `crypto-scout-analyst` focusing on RabbitMQ Streams and the offset store.

## RabbitMQ

- **Streams**
  - `amqp.crypto.bybit.stream`: source stream (raw BYBIT events)
  - `amqp.crypto.bybit.ta.stream`: destination stream (analyzed events)
- **Required config keys** (see `com.github.akarazhev.cryptoscout.config.AmqpConfig`):
  - `amqp.rabbitmq.host`
  - `amqp.rabbitmq.port`
  - `amqp.stream.port`
  - `amqp.rabbitmq.username`
  - `amqp.rabbitmq.password`
  - `amqp.crypto.bybit.stream`
  - `amqp.crypto.bybit.ta.stream`

Ensure RabbitMQ Streams are enabled and the two streams exist. The application uses the RabbitMQ Streams Java Client to consume and publish.

## PostgreSQL

The service persists consumer offsets in a table referenced by `com.github.akarazhev.cryptoscout.analyst.db.Constants.Offsets`.

- **Required config keys** (see `com.github.akarazhev.cryptoscout.config.JdbcConfig`):
  - `jdbc.datasource.url`
  - `jdbc.datasource.username`
  - `jdbc.datasource.password`
  - Optional HikariCP tuning:
    - `jdbc.hikari.maximum-pool-size`
    - `jdbc.hikari.minimum-idle`
    - `jdbc.hikari.connection-timeout-ms`
    - `jdbc.hikari.idle-timeout-ms`
    - `jdbc.hikari.max-lifetime-ms`
    - `jdbc.hikari.register-mbeans`

- **Offsets table DDL (minimal):**

```sql
CREATE SCHEMA IF NOT EXISTS crypto_scout;

CREATE TABLE IF NOT EXISTS crypto_scout.stream_offsets (
    stream      text PRIMARY KEY,
    "offset"   bigint,
    updated_at  timestamp without time zone DEFAULT now()
);
```

This schema satisfies the SQL used by `StreamOffsetsRepository`:
- Select: `SELECT "offset" FROM crypto_scout.stream_offsets WHERE stream = ?`
- Upsert: `INSERT ... ON CONFLICT (stream) DO UPDATE SET "offset" = EXCLUDED."offset", updated_at = NOW()`

## Service lifecycle

`CryptoBybitAnalyst` wires a pipeline:
- RabbitMQ consumer reads from `amqp.crypto.bybit.stream` and enqueues messages
- ActiveJ Datastream pipeline
  - Decode message bytes to `Payload` (`BytesToPayloadTransformer`)
  - Run analysis stub (`BybitAnalysisTransformer`)
  - Publish to `amqp.crypto.bybit.ta.stream` and commit offsets (`RabbitStreamPublisher`)

Offsets are committed after successful publish (or skipped publish if analysis decides to drop), ensuring at-least-once semantics for the source stream.
