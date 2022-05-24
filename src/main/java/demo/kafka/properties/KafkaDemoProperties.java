package demo.kafka.properties;

import java.net.URL;
import java.util.UUID;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties("kafkademo")
@Getter
@Setter
@Validated
public class KafkaDemoProperties {
    @NotNull private String id;
    @NotNull private URL thirdpartyEndpoint;
    @NotNull private String outboundTopic;

    // A unique Id for this instance of the service.
    @NotNull private UUID instanceId = UUID.randomUUID();
}
