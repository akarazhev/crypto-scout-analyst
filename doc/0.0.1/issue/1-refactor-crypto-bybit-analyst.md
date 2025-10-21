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
