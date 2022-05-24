package demo.kafka.lib;

import java.util.UUID;

import demo.kafka.exception.KafkaDemoException;
import demo.kafka.properties.KafkaDemoProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaClient {
    @Autowired
    private KafkaDemoProperties properties;

    @Autowired
    private KafkaTemplate kafkaTemplate;

    public static final String EVENT_ID_HEADER_KEY = "demo_eventIdHeader";

    public SendResult sendMessage(String key, String data) {
        try {
            String payload = "eventId: " + UUID.randomUUID() + ", instanceId: "+properties.getInstanceId()+", payload: " + data;
            final ProducerRecord<String, String> record =
                    new ProducerRecord<>(properties.getOutboundTopic(), key, payload);

            final SendResult result = (SendResult) kafkaTemplate.send(record).get();
            final RecordMetadata metadata = result.getRecordMetadata();

            log.debug(String.format("Sent record(key=%s value=%s) meta(topic=%s, partition=%d, offset=%d)",
                    record.key(), record.value(), metadata.topic(), metadata.partition(), metadata.offset()));

            return result;
        } catch (Exception e) {
            log.error("Error sending message to topic " + properties.getOutboundTopic(), e);
            throw new KafkaDemoException(e);
        }
    }
}
