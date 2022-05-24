package demo.kafka.component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import dev.lydtech.component.framework.client.debezium.DebeziumClient;
import dev.lydtech.component.framework.client.kafka.KafkaClient;
import dev.lydtech.component.framework.client.wiremock.WiremockClient;
import dev.lydtech.component.framework.extension.TestContainersSetupExtension;
import dev.lydtech.component.framework.mapper.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ActiveProfiles;

import static demo.kafka.util.TestEventData.INBOUND_DATA;
import static demo.kafka.util.TestEventData.buildDemoInboundEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * Demonstrates the Transactional Outbox pattern.
 */
@Slf4j
@ExtendWith(TestContainersSetupExtension.class)
@ActiveProfiles("component-test")
public class TransactionalOutboxCT {

    private static final String GROUP_ID = "TransactionalOutboxCT";
    private Consumer consumer;

    @BeforeEach
    public void setup() {
        consumer = KafkaClient.getInstance().createConsumer(GROUP_ID, "demodbserver.kafka_demo_idempotent_consumer.outbox_event");
        WiremockClient.getInstance().resetMappings();
        DebeziumClient.getInstance().createConnector("connector/kafka-idempotent-consumer-demo-connector.json");

        // Clear the topic.
        consumer.poll(Duration.ofSeconds(1));
    }

    @AfterEach
    public void tearDown() {
        DebeziumClient.getInstance().deleteConnector("kafka-idempotent-consumer-demo-connector");
        consumer.close();
    }

    /**
     * The outbound event is emitted via the transactional outbox, with Debezium writing the event to Kafka.
     *
     * The outbound event should have the original payload in its payload.
     */
    @Test
    public void testIdempotentConsumerAndTransactionalOutbox() throws Exception {
        WiremockClient.getInstance().postMappingFile("thirdParty/success.json");

        String key = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        KafkaClient.getInstance().sendMessage("demo-idempotent-with-outbox-inbound-topic", key, JsonMapper.writeToJson(buildDemoInboundEvent(key)), Collections.singletonMap("demo_eventIdHeader", eventId));
        List<ConsumerRecord<String, String>> outboundEvents = KafkaClient.getInstance().consumeAndAssert("IdempotentConsumer-Transactional-Outbox", consumer, 1, 3);
        assertThat(outboundEvents.get(0).value(), containsString(INBOUND_DATA));
    }

    /**
     * The REST call to the third party service during the event processing is wiremocked to fail twice before success.
     *
     * The event is retried (with stateless retry), and this exceeds the consumer poll timeout, so the message is
     * redelivered to a second consumer instance.
     *
     * The consumer is idempotent, so the message is deduplicated.  The outbound event is emitted via the
     * transactional outbox, with Debezium writing the event to Kafka.
     *
     * The outbound event should have the original payload in its payload.
     */
    @Test
    public void testIdempotentConsumerAndTransactionalOutboxWithDeduplication() throws Exception {
        WiremockClient.getInstance().postMappingFile("thirdParty/retry_behaviour_01_start-to-unavailable.json");
        WiremockClient.getInstance().postMappingFile("thirdParty/retry_behaviour_02_unavailable-to-success.json");
        WiremockClient.getInstance().postMappingFile("thirdParty/retry_behaviour_03_success.json");

        String key = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        KafkaClient.getInstance().sendMessage("demo-idempotent-with-outbox-inbound-topic", key, JsonMapper.writeToJson(buildDemoInboundEvent(key)), Collections.singletonMap("demo_eventIdHeader", eventId));
        List<ConsumerRecord<String, String>> outboundEvents = KafkaClient.getInstance().consumeAndAssert("IdempotentConsumer-Transactional-Outbox-With-Deduplication", consumer, 1, 3);
        assertThat(outboundEvents.get(0).value(), containsString(INBOUND_DATA));
    }
}
