package demo.kafka.repository;

import java.util.UUID;

import demo.kafka.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
}
