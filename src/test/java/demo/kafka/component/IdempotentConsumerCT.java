package demo.kafka.component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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

import static demo.kafka.util.TestEventData.INBOUND_DATA;
import static demo.kafka.util.TestEventData.buildDemoInboundEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * With a call to a third party service that is configured to fail on the two attempts, with a 6 second retry delay
 * between each retry then the 10 second consumer poll timeout (configured in application-component-test.yml) is
 * exceeded.  For stateless retry this results in the consumer instance being removed from the consumer group by the
 * Kafka broker, and the messags is then re-polled by the second consumer instance.
 *
 * If the consumer deduplicates the events then only one resulting outbound event will be published, but if the consumer
 * does not deduplicate the events then an outbound event will be published for each duplicate consumed.
 */
@Slf4j
@ExtendWith(TestContainersSetupExtension.class)
public class IdempotentConsumerCT {

    private static final String GROUP_ID = "IdempotentConsumerCT";

    private Consumer consumer;

    /**
     * Configure the wiremock to return a 503 twice times before success.
     */
    @BeforeEach
    public void setup() {
        consumer = KafkaClient.getInstance().createConsumer(GROUP_ID, "demo-outbound-topic");

        WiremockClient.getInstance().resetMappings();
        WiremockClient.getInstance().postMappingFile("thirdParty/retry_behaviour_01_start-to-unavailable.json");
        WiremockClient.getInstance().postMappingFile("thirdParty/retry_behaviour_02_unavailable-to-success.json");
        WiremockClient.getInstance().postMappingFile("thirdParty/retry_behaviour_03_success.json");

        // Clear the topic.
        consumer.poll(Duration.ofSeconds(1));
    }

    @AfterEach
    public void tearDown() {
        consumer.close();
    }

    /**
     * Stateless retry means the retries will exceed the poll timeout, so the message will be redelivered.
     *
     * The idempotent consumer will deduplicate the message using the event Id in the header.
     *
     * The outbound event should have the original payload in its payload.
     */
    @Test
    public void testIdempotentConsumer() throws Exception {
        String key = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        KafkaClient.getInstance().sendMessage("demo-idempotent-inbound-topic", key, JsonMapper.writeToJson(buildDemoInboundEvent(key)), Collections.singletonMap("demo_eventIdHeader", eventId));
        List<ConsumerRecord<String, String>> outboundEvents = KafkaClient.getInstance().consumeAndAssert("IdempotentConsumer", consumer, 1, 3);
        assertThat(outboundEvents.get(0).value(), containsString(INBOUND_DATA));
    }

    /**
     * Stateless retry means the retries will exceed the poll timeout, so the message will be redelivered.
     *
     * The consumer is not idempotent, so the message will not be deduplicated, resulting in two outbound events.
     *
     * The duplicate outbound events should have the original payload in their payload.
     */
    @Test
    public void testNonIdempotentConsumer() throws Exception {
        String key = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        KafkaClient.getInstance().sendMessage("demo-non-idempotent-inbound-topic", key, JsonMapper.writeToJson(buildDemoInboundEvent(key)), Collections.singletonMap("demo_eventIdHeader", eventId));
        List<ConsumerRecord<String, String>> outboundEvents = KafkaClient.getInstance().consumeAndAssert("NonIdempotentConsumer", consumer, 2, 3);
        outboundEvents.stream().forEach(outboundEvent -> assertThat(outboundEvent.value(), containsString(INBOUND_DATA)));
    }
}
