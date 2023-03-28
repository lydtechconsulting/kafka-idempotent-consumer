# Kafka Idempotent Consumer & Transactional Outbox

Spring Boot application demonstrating the Kafka Idempotent Consumer pattern and Transactional Outbox pattern with 
Debezium (Kafka Connect) used for Change Data Capture (CDC) to publish outbound events.

This repo accompanies the article [Kafka Idempotent Consumer & Transactional Outbox](https://medium.com/lydtech-consulting/kafka-idempotent-consumer-transactional-outbox-74b304815550).

## Integration Tests

Build and test with maven and Java 11.

Run integration tests with `mvn clean test`

The tests demonstrate event deduplication with the Idempotent Consumer pattern when duplicate events are consumed by the 
application.

## Component Tests

The tests demonstrate event deduplication when duplicate events are consumed by the application using the Idempotent
Consumer pattern, as well publishing events using the Transactional Outbox pattern with Debezium (Kafka Connect) for 
Change Data Capture.   They use a dockerised Kafka broker, a dockerised Debezium Kafka Connect, a dockerised Postgres 
database, and a dockerised wiremock to represent a third party service.  

This call to the third party service simulates transient errors that can be successful on retry.  The delay caused by 
retry can cause duplicate message delivery, enabling demonstration of event deduplication.

Two instances of the service are also running in docker containers.

For more on the component tests see: https://github.com/lydtechconsulting/component-test-framework

Build Spring Boot application jar:
```
mvn clean install
```

Build Docker container:
```
docker build -t ct/kafka-idempotent-consumer:latest .
```

Run tests:
```
mvn test -Pcomponent
```

Run tests leaving containers up:
```
mvn test -Pcomponent -Dcontainers.stayup
```

Manual clean up (if left containers up):
```
docker rm -f $(docker ps -aq)
```

# Kafka Connect / Debezium

## Create Postgres connector

The Debezium Postgres source connector configuration is defined in `src/test/resources/connector/kafka-idempotent-consumer-demo-connector.json`.

It includes a Single Message Transform (SMT) that routes the outbox event to the value of the destination field in the 
outbox event database table.

The component tests create and delete the connector via the `KafkaConnectAdminClient` class. 
