package io.github.coldzero7362.pdfsign.document;

import io.github.coldzero7362.pdfsign.audit.AuditService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 서명 요청 기간이 지난 문서를 EXPIRED로 바꾼다. 접근할 때 바로 확인하고, 10분마다 한꺼번에도 정리한다. */
@Component
public class DocumentExpiry {

    private final DocumentRepository documents;
    private final AuditService audit;
    private final Clock clock;

    public DocumentExpiry(DocumentRepository documents, AuditService audit, Clock clock) {
        this.documents = documents;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void expireIfDue(Document d, Instant now) {
        if (d.getStatus().isOpen() && d.isPastExpiry(now)) {
            d.expire(now);
            audit.record(d.getId(), null, "system", "EXPIRED", null);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sweepOwner(Long ownerId) {
        Instant now = clock.instant();
        documents.findByStatusAndExpiresAtLessThanEqual(DocumentStatus.IN_PROGRESS, now).stream()
            .filter(d -> d.getOwnerId().equals(ownerId))
            .forEach(d -> expireIfDue(d, now));
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
    @Transactional
    public void sweep() {
        Instant now = clock.instant();
        documents.findByStatusAndExpiresAtLessThanEqual(DocumentStatus.IN_PROGRESS, now)
            .forEach(d -> expireIfDue(d, now));
    }
}
