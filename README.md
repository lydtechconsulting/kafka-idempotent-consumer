# Kafka Idempotent Consumer & Transactional Outbox

Spring Boot application demonstrating the Kafka Idempotent Consumer pattern and Transactional Outbox pattern with 
Debezium (Kafka Connect) used for Change Data Capture (CDC) to publish outbound events.

This repo accompanies the article [Kafka Idempotent Consumer & Transactional Outbox](https://medium.com/lydtech-consulting/kafka-idempotent-consumer-transactional-outbox-74b304815550).

## Running The Demo

The demo steps are as follows (and detailed below):
- Start the Docker containers (Kafka, Zookeeper, Postgres, Debezium, Wiremock) via docker-compose.
- Add the wiremock behaviour for the third party service call to return a success, via curl.
- Submit the kafka connect connector definition via curl.
- Start the Spring Boot demo application.
- Start a console-consumer to listen on the outbox topic.
- Start a console-producer to submit an event to the inbound topic.
- The application consumes the event from the inbound topic and writes to the outbox table.  Debezium writes an event to the outbound topic via Change Data Capture (CDC).  The console-consumer consumes this event.

Demo steps breakdown:

Build Spring Boot application with Java 17:
```
mvn clean install
```

Start Docker containers:
```
docker-compose up -d
```

Add wiremock behaviour (for third party service call):
```
curl -i -X POST http://localhost:9002/__admin/mappings/new -H "Accept: application/json" -d @./src/test/resources/thirdparty/success.json  
```

View added mapping:
```
curl -i http://localhost:9002/__admin/mappings
```

Check status of Kafka Connect:
```
curl localhost:8083
```

List registered connectors:
```
curl localhost:8083/connectors
```

Register connector:
```
curl -X POST localhost:8083/connectors -H "Content-Type: application/json" -d @./connector/kafka-idempotent-consumer-demo-connector.json
```

List registered connectors:
```
curl localhost:8083/connectors
["kafka-idempotent-consumer-demo-connector"]
```

Start Spring Boot application:
```
java -jar target/kafka-idempotent-consumer-2.0.0.jar
```

Jump onto Kafka docker container:
```
docker exec -ti kafka bash
```

Start console-consumer to listen for outbox event:
```
kafka-console-consumer \
--topic demo.kafka_demo_idempotent_consumer.outbox_event \
--bootstrap-server kafka:29092
```

In a second terminal window jump onto Kafka docker container again, and produce a message to the `demo-idempotent-with-outbox-inbound-topic` topic:
```
kafka-console-producer \
--topic demo-idempotent-with-outbox-inbound-topic \
--bootstrap-server kafka:29092 \
--property parse.headers=true \
--property parse.key=true
```

Now enter the message to create the item.  Enter the following on a single line, with TABs where indicated:
```
demo_eventIdHeader:a8c5614f-f286-4c4d-bbb4-11a7de7d8095
TAB
cf1e0b87-5f91-42cb-a637-cb904e5ae1fa
TAB
{"id": "e237ad90-272f-492a-9684-7f6c70613b02", "data": "test-data"}
```

N.B. the offered separator overrides were not being respected, so required to enter TAB characters.

View outbox event consumed by the console-consumer from Kafka.

Stop containers:
```
docker-compose down
```

## Integration Tests

Build and test with maven and Java 17.

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

## Kafka Connect / Debezium

### Create Postgres connector

The Debezium Postgres source connector configuration is defined in `connector/kafka-idempotent-consumer-demo-connector.json`.

It includes a Single Message Transform (SMT) that routes the outbox event to the value of the destination field in the 
outbox event database table.

The component tests create and delete the connector via the `DebeziumClient` class in the `component-test-framework`.

## Docker Clean Up

Manual clean up (if left containers up):
```
docker rm -f $(docker ps -aq)
```

Further docker clean up if network/other issues:
```
docker system prune
docker volume prune
```
