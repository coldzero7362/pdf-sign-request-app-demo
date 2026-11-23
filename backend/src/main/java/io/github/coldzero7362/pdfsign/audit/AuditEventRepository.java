package io.github.coldzero7362.pdfsign.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByRequestIdOrderByCreatedAtAscIdAsc(Long requestId);

    List<AuditEvent> findByRecipientIdAndEventTypeAndCreatedAtAfterOrderByCreatedAtDesc(
        Long recipientId, String eventType, java.time.Instant after);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AuditEvent e where e.requestId = :requestId")
    void deleteByRequestId(Long requestId);
}
