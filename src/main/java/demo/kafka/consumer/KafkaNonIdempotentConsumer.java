package demo.kafka.consumer;

import java.util.concurrent.atomic.AtomicInteger;

import demo.kafka.event.DemoInboundEvent;
import demo.kafka.exception.DuplicateEventException;
import demo.kafka.exception.Retryable;
import demo.kafka.mapper.JsonMapper;
import demo.kafka.service.DemoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class KafkaNonIdempotentConsumer {

    final AtomicInteger counter = new AtomicInteger();
    final DemoService demoRetryService;

    @KafkaListener(topics = "demo-non-idempotent-inbound-topic", groupId = "kafkaConsumerGroup", containerFactory = "kafkaListenerContainerFactory")
    public void listen(@Header(KafkaHeaders.RECEIVED_KEY) String key, @Payload final String payload) {
        counter.getAndIncrement();
        log.debug("Received message [" +counter.get()+ "] - key: " + key + " - payload: " + payload);
        try {
            DemoInboundEvent event = JsonMapper.readFromJson(payload, DemoInboundEvent.class);
            demoRetryService.processNonIdempotent(key, event);
        } catch (DuplicateEventException e) {
            // Update consumer offsets to ensure event is not again redelivered.
            log.debug("Duplicate message received: "+ e.getMessage());
        } catch (Exception e) {
            if (e instanceof Retryable) {
                log.debug("Throwing retryable exception.");
                throw e;
            }
            log.error("Error processing message: " + e.getMessage());
        }
    }
}
