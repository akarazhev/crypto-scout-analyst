# Issue 3: Implement tests for data service

In this `crypto-scout-analyst` project we are going to implement tests for data service, this service is responsible for
reading streaming data from the `rabbitmq` server and processing it.

## Roles

Take the following roles:

- Expert java engineer.

## Conditions

- Rely on the current `crypto-scout-analyst` project and related ones: `crypto-scout-test`, `crypto-scout-mq`,
  `crypto-scout-collector`, `crypto-scout-client` which can be found in the workspaces.
- Double-check your proposal and make sure that they are correct and haven't missed any important points.
- Implementation must be production ready.
- Use the best practices and design patterns.

## Constraints

- Use the current technological stack, that's: `ActiveJ 6.0`, `Java 25`, `maven 3.9.1`, `podman 5.6.2`,
  `podman-compose 1.5.0`.
- Follow the current code style.
- Do not hallucinate.

## Tasks

- As the `expert java engineer` review the current implementation of the @DataService.java and implement tests for this
  in @DataServiceTest.java.
- As the `expert java engineer` address and review implementations of the similar services in the
  `crypto-scout-collector` project, test samples and helpers can be found in the `crypto-scout-test` project.
- As the `expert java engineer` recheck your proposal and make sure that they are correct and haven't missed any
  important points.