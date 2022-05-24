package demo.kafka.integration;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import demo.kafka.event.DemoInboundEvent;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static demo.kafka.util.TestEventData.buildDemoInboundEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Slf4j
@EmbeddedKafka(controlledShutdown = true, topics = { "demo-idempotent-inbound-topic", "demo-non-idempotent-inbound-topic" })
public class KafkaIdempotentConsumerIntegrationTest extends IntegrationTestBase {

    final static String DEMO_IDEMPOTENT_TEST_TOPIC = "demo-idempotent-inbound-topic";
    final static String DEMO_NON_IDEMPOTENT_TEST_TOPIC = "demo-non-idempotent-inbound-topic";

    @Autowired
    private KafkaTestListener testReceiver;

    @Configuration
    static class TestConfig {

        @Bean
        public KafkaTestListener testReceiver() {
            return new KafkaTestListener();
        }
    }

    /**
     * Use this receiver to consume messages from the outbound topic.
     */
    public static class KafkaTestListener {
        AtomicInteger counter = new AtomicInteger(0);

        @KafkaListener(groupId = "KafkaIdempotentConsumerIntegrationTest", topics = "demo-outbound-topic", autoStartup = "true")
        void receive(@Payload final String payload, @Headers final MessageHeaders headers) {
            log.debug("KafkaTestListener - Received message: " + payload);
            counter.incrementAndGet();
        }
    }

    @BeforeEach
    public void setUp() {
        super.setUp();
        testReceiver.counter.set(0);
    }

    @Test
    public void testSuccess() throws Exception {
        String key = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        stubWiremock("/api/kafkaidempotentconsumerdemo/" + key, 200, "Success");

        sendMessage(DEMO_IDEMPOTENT_TEST_TOPIC, eventId, key, buildDemoInboundEvent(key));

        // Check for a message being emitted on demo-outbound-topic
        Awaitility.await().atMost(10, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
                .until(testReceiver.counter::get, equalTo(1));
        verify(exactly(1), getRequestedFor(urlEqualTo("/api/kafkaidempotentconsumerdemo/" + key)));
    }

    /**
     * Send in three events and show two are deduplicated by the idempotent consumer, as only one outbound event is emitted.
     */
    @Test
    public void testEventDeduplication_IdempotentConsumer() throws Exception {
        String key = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        stubWiremock("/api/kafkaidempotentconsumerdemo/" + key, 200, "Success");

        DemoInboundEvent inboundEvent = buildDemoInboundEvent(key);
        sendMessage(DEMO_IDEMPOTENT_TEST_TOPIC, eventId, key, inboundEvent);
        sendMessage(DEMO_IDEMPOTENT_TEST_TOPIC, eventId, key, inboundEvent);
        sendMessage(DEMO_IDEMPOTENT_TEST_TOPIC, eventId, key, inboundEvent);

        // Check for a message being emitted on demo-outbound-topic
        Awaitility.await().atMost(10, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
                .until(testReceiver.counter::get, equalTo(1));

        // Now check the duplicate event has been deduplicated.
        TimeUnit.SECONDS.sleep(5);
        assertThat(testReceiver.counter.get(), equalTo(1));
        verify(exactly(1), getRequestedFor(urlEqualTo("/api/kafkaidempotentconsumerdemo/" + key)));
    }

    /**
     * Send in three events and show none are deduplicated by the non-idempotent consumer, as three outbound events are emitted.
     */
    @Test
    public void testEventDeduplication_NonIdempotentConsumer() throws Exception {
        String key = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        stubWiremock("/api/kafkaidempotentconsumerdemo/" + key, 200, "Success");

        DemoInboundEvent inboundEvent = buildDemoInboundEvent(key);
        sendMessage(DEMO_NON_IDEMPOTENT_TEST_TOPIC, eventId, key, inboundEvent);
        sendMessage(DEMO_NON_IDEMPOTENT_TEST_TOPIC, eventId, key, inboundEvent);
        sendMessage(DEMO_NON_IDEMPOTENT_TEST_TOPIC, eventId, key, inboundEvent);

        // Check for three messages being emitted on demo-outbound-topic
        Awaitility.await().atMost(10, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
                .until(testReceiver.counter::get, equalTo(3));
        verify(exactly(3), getRequestedFor(urlEqualTo("/api/kafkaidempotentconsumerdemo/" + key)));
    }
}
