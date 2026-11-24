package io.github.coldzero7362.pdfsign.audit;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByDocumentIdOrderByCreatedAtAscIdAsc(Long documentId);

    List<AuditEvent> findByRecipientIdAndEventTypeAndCreatedAtAfterOrderByCreatedAtDesc(
        Long recipientId, String eventType, Instant after);
}
