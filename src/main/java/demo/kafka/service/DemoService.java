package demo.kafka.service;

import java.util.UUID;

import demo.kafka.domain.OutboxEvent;
import demo.kafka.domain.ProcessedEvent;
import demo.kafka.event.DemoInboundEvent;
import demo.kafka.exception.DuplicateEventException;
import demo.kafka.exception.KafkaDemoException;
import demo.kafka.exception.KafkaDemoRetryableException;
import demo.kafka.lib.KafkaClient;
import demo.kafka.properties.KafkaDemoProperties;
import demo.kafka.repository.OutboxEventRepository;
import demo.kafka.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class DemoService {;

    @Autowired
    private KafkaDemoProperties properties;

    @Autowired
    private KafkaClient kafkaClient;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Transactional
    public void processIdempotent(String eventId, String key, DemoInboundEvent event) {
        deduplicate(UUID.fromString(eventId));
        callThirdparty(key);
        kafkaClient.sendMessage(key, event.getData());
    }

    @Transactional
    public void processNonIdempotent(String key, DemoInboundEvent event) {
        callThirdparty(key);
        kafkaClient.sendMessage(key, event.getData());
    }

    @Transactional
    public void processIdempotentAndOutbox(String eventId, String key, DemoInboundEvent event) {
        deduplicate(UUID.fromString(eventId));
        callThirdparty(key);
        writeOutboxEvent(event.getData());
    }

    private void deduplicate(UUID eventId) throws DuplicateEventException {
        try {
            processedEventRepository.saveAndFlush(new ProcessedEvent(eventId));
            log.debug("Event persisted with Id: {}", eventId);
        } catch (DataIntegrityViolationException e) {
            log.warn("Event already processed: {}", eventId);
            throw new DuplicateEventException(eventId);
        }
    }

    private void callThirdparty(String key) {
        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(properties.getThirdpartyEndpoint() + "/" + key, String.class);
            if (response.getStatusCodeValue() != 200) {
                throw new RuntimeException("error " + response.getStatusCodeValue());
            }
            return;
        } catch (HttpServerErrorException e) {
            log.error("Error calling thirdparty api, returned an (" + e.getClass().getName() + ") with an error code of " + e.getRawStatusCode(), e);   // e.getRawStatusCode()
            throw new KafkaDemoRetryableException(e);
        } catch (ResourceAccessException e) {
            log.error("Error calling thirdparty api, returned an (" + e.getClass().getName() + ")", e);
            throw new KafkaDemoRetryableException(e);
        } catch (Exception e) {
            log.error("Error calling thirdparty api, returned an (" + e.getClass().getName() + ")", e);
            throw new KafkaDemoException(e);
        }
    }

    private void writeOutboxEvent(String payload) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .version("v1")
                .payload(payload)
                .destination("demo-outbox-outbound")
                .timestamp(System.currentTimeMillis())
                .build();
        UUID outboxEventId = outboxEventRepository.save(outboxEvent).getId();
        log.debug("Event persisted to transactional outbox with Id: {}", outboxEventId);
    }
}
