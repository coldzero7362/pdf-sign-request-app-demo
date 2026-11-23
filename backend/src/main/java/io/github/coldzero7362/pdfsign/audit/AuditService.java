package io.github.coldzero7362.pdfsign.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 요청별 감사 기록. IP와 브라우저 정보는 현재 HTTP 요청에서 가져온다. */
@Service
public class AuditService {

    private final AuditEventRepository events;
    private final Clock clock;

    public AuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long requestId, Long recipientId, String actor, String type, String detail) {
        String ip = null;
        String ua = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            ip = req.getRemoteAddr();
            ua = truncate(req.getHeader("User-Agent"), 255);
        }
        events.save(new AuditEvent(requestId, recipientId, actor, type, truncate(detail, 1000), ip, ua,
            clock.instant()));
    }

    /** 초안을 지울 때만 쓴다. 발송한 요청의 기록은 지우지 않는다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteForDraft(Long requestId) {
        events.deleteByRequestId(requestId);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> list(Long requestId) {
        return events.findByRequestIdOrderByCreatedAtAscIdAsc(requestId);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
