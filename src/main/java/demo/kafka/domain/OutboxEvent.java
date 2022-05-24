package demo.kafka.domain;

import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

@Entity(name="OutboxEvent")
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    public static final int VARCHAR_MAX_LENGTH = 4096;

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    private UUID id;

    @Column(nullable = false, length = VARCHAR_MAX_LENGTH)
    private String payload;

    @Column(nullable = false)
    private long timestamp;

    @Column(nullable = false)
    private String destination;

    private String version;
}
