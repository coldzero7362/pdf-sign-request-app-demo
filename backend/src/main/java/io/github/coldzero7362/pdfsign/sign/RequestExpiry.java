package io.github.coldzero7362.pdfsign.sign;

import io.github.coldzero7362.pdfsign.audit.AuditService;
import io.github.coldzero7362.pdfsign.request.RequestStatus;
import io.github.coldzero7362.pdfsign.request.SignRequest;
import io.github.coldzero7362.pdfsign.request.SignRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 기한이 지난 요청을 EXPIRED로 바꾼다. 접근할 때 바로 확인하고, 10분마다 한꺼번에도 정리한다. */
@Component
public class RequestExpiry {

    private final SignRequestRepository requests;
    private final AuditService audit;
    private final Clock clock;

    public RequestExpiry(SignRequestRepository requests, AuditService audit, Clock clock) {
        this.requests = requests;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void expireIfDue(SignRequest r, Instant now) {
        if (r.getStatus().isOpen() && r.isPastExpiry(now)) {
            r.expire(now);
            audit.record(r.getId(), null, "system", "EXPIRED", null);
        }
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
    @Transactional
    public void sweep() {
        Instant now = clock.instant();
        List<SignRequest> due = requests.findByStatusInAndExpiresAtLessThanEqual(
            List.of(RequestStatus.SENT, RequestStatus.IN_PROGRESS), now);
        due.forEach(r -> expireIfDue(r, now));
    }
}
