package demo.kafka.integration;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import demo.kafka.domain.OutboxEvent;
import demo.kafka.event.DemoInboundEvent;
import demo.kafka.repository.OutboxEventRepository;
import demo.kafka.util.TestEventData;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.context.EmbeddedKafka;

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static demo.kafka.util.TestEventData.buildDemoInboundEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Slf4j
@EmbeddedKafka(controlledShutdown = true, topics = "demo-idempotent-with-outbox-inbound-topic")
public class KafkaTransactionalOutboxIntegrationTest extends IntegrationTestBase {

    final static String DEMO_IDEMPOTENT_AND_OUTBOX_TEST_TOPIC = "demo-idempotent-with-outbox-inbound-topic";

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    public void setUp() {
        super.setUp();
        outboxEventRepository.deleteAll();
    }

    /**
     * Send in multiple events and verify a single outbound event is written to the outbox event table.
     */
    @Test
    public void testEventDeduplication_TransactionalOutbox() throws Exception {
        String key = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        stubWiremock("/api/kafkaidempotentconsumerdemo/" + key, 200, "Success");

        DemoInboundEvent inboundEvent = buildDemoInboundEvent(key);
        sendMessage(DEMO_IDEMPOTENT_AND_OUTBOX_TEST_TOPIC, eventId, key, inboundEvent);
        sendMessage(DEMO_IDEMPOTENT_AND_OUTBOX_TEST_TOPIC, eventId, key, inboundEvent);
        sendMessage(DEMO_IDEMPOTENT_AND_OUTBOX_TEST_TOPIC, eventId, key, inboundEvent);

        // Check an event has been written to the outbox table.
        Awaitility.await().atMost(10, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
                .until(outboxEventRepository::count, equalTo(1L));

        // Ensure no duplicate event is written to the outbox table.
        TimeUnit.SECONDS.sleep(5);
        verify(exactly(1), getRequestedFor(urlEqualTo("/api/kafkaidempotentconsumerdemo/" + key)));

        assertThat(outboxEventRepository.count(), equalTo(1L));
        OutboxEvent outboxEvent = outboxEventRepository.findAll().get(0);
        assertThat(outboxEvent.getPayload(), equalTo(TestEventData.INBOUND_DATA));
    }
}
