package io.github.coldzero7362.pdfsign.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 문서별 감사 기록. IP와 브라우저 정보는 현재 HTTP 요청에서 가져온다. */
@Service
public class AuditService {

    private final AuditEventRepository events;
    private final Clock clock;

    public AuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long documentId, Long recipientId, String actor, String type, String detail) {
        record(documentId, recipientId, null, actor, type, detail);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long documentId, Long recipientId, Long relatedDocumentId, String actor, String type,
                       String detail) {
        String ip = null;
        String ua = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            ip = req.getRemoteAddr();
            ua = truncate(req.getHeader("User-Agent"), 255);
        }
        events.save(new AuditEvent(documentId, recipientId, relatedDocumentId, actor, type, truncate(detail, 1000),
            ip, ua, clock.instant()));
    }

    /** 이 수신자에게 since 이후 기록된 이벤트 (최근 것부터). */
    @Transactional(readOnly = true)
    public List<AuditEvent> recentFor(Long recipientId, String type, Instant since) {
        return events.findByRecipientIdAndEventTypeAndCreatedAtAfterOrderByCreatedAtDesc(recipientId, type, since);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> list(Long documentId) {
        return events.findByDocumentIdOrderByCreatedAtAscIdAsc(documentId);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
