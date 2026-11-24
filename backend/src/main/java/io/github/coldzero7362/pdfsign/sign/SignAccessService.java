package io.github.coldzero7362.pdfsign.sign;

import io.github.coldzero7362.pdfsign.audit.AuditService;
import io.github.coldzero7362.pdfsign.common.Tokens;
import io.github.coldzero7362.pdfsign.document.Document;
import io.github.coldzero7362.pdfsign.document.DocumentExpiry;
import io.github.coldzero7362.pdfsign.document.DocumentRepository;
import io.github.coldzero7362.pdfsign.document.Recipient;
import io.github.coldzero7362.pdfsign.document.RecipientRepository;
import io.github.coldzero7362.pdfsign.document.RecipientStatus;
import io.github.coldzero7362.pdfsign.mail.MailOutbox;
import io.github.coldzero7362.pdfsign.mail.MailTemplates;
import io.github.coldzero7362.pdfsign.sign.SignDtos.LinkInfo;
import io.github.coldzero7362.pdfsign.sign.SignDtos.OtpSent;
import io.github.coldzero7362.pdfsign.user.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 수신자 본인 확인: 메일 링크 토큰 → 이메일 인증번호 → HTTP 세션 (지침 18).
 *
 * - 토큰과 인증번호는 해시만 저장한다.
 * - 인증번호: 6자리, 10분 유효, 5번 틀리면 무효, 재발송 60초 간격, 1시간에 5번까지.
 * - 확인에 성공하면 세션 ID를 새로 발급한다 (세션 고정 방지).
 * - 진행 중에는 차례가 온 서명자만, 완료 후 30일 동안은 완료 링크를 받은 모두가 들어온다 (지침 12).
 */
@Service
public class SignAccessService {

    static final Duration OTP_TTL = Duration.ofMinutes(10);
    static final Duration OTP_COOLDOWN = Duration.ofSeconds(60);
    static final Duration OTP_WINDOW = Duration.ofHours(1);
    static final int OTP_MAX_SENDS = 5;
    static final int OTP_MAX_ATTEMPTS = 5;
    static final Duration SESSION_TTL = Duration.ofHours(2);
    public static final Duration RESULT_ACCESS = Duration.ofDays(30);

    private final RecipientRepository recipients;
    private final DocumentRepository documents;
    private final AppUserRepository users;
    private final AuditService audit;
    private final MailOutbox mail;
    private final MailTemplates templates;
    private final DocumentExpiry expiry;
    private final Clock clock;

    public SignAccessService(RecipientRepository recipients, DocumentRepository documents, AppUserRepository users,
                             AuditService audit, MailOutbox mail, MailTemplates templates, DocumentExpiry expiry,
                             Clock clock) {
        this.recipients = recipients;
        this.documents = documents;
        this.users = users;
        this.audit = audit;
        this.mail = mail;
        this.templates = templates;
        this.expiry = expiry;
        this.clock = clock;
    }

    public record Access(Recipient recipient, Document document) {
    }

    @Transactional
    public LinkInfo linkInfo(String token, HttpSession session) {
        Access a = byToken(token);
        RecipientSession current = session == null ? null : (RecipientSession) session.getAttribute(RecipientSession.ATTR);
        boolean verified = current != null && current.recipientId().equals(a.recipient().getId()) && isFresh(current);
        String sender = users.findById(a.document().getOwnerId()).map(u -> u.getName()).orElse("");
        return new LinkInfo(a.document().getTitle(), sender, mask(a.recipient().getEmail()), a.recipient().getRole(),
            a.document().getStatus(), verified);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public OtpSent sendOtp(String token) {
        Access a = byToken(token);
        Recipient r = a.recipient();
        Instant now = clock.instant();
        if (r.getOtpLastSentAt() != null && now.isBefore(r.getOtpLastSentAt().plus(OTP_COOLDOWN))) {
            long wait = Duration.between(now, r.getOtpLastSentAt().plus(OTP_COOLDOWN)).toSeconds() + 1;
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, wait + "초 뒤에 다시 받을 수 있습니다.");
        }
        if (r.otpSendsInWindow(now, OTP_WINDOW) >= OTP_MAX_SENDS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "인증번호 요청이 너무 많습니다. 1시간 뒤에 다시 시도해 주세요.");
        }
        String code = Tokens.otp();
        r.issueOtp(otpHash(r, code), now, now.plus(OTP_TTL), OTP_WINDOW);
        audit.record(a.document().getId(), r.getId(), r.getEmail(), "OTP_SENT", null);
        mail.send(templates.otp(r.getEmail(), code, a.document().getTitle()));
        return new OtpSent(mask(r.getEmail()), (int) OTP_COOLDOWN.toSeconds());
    }

    /** 틀린 횟수는 오류를 던져도 저장해야 하므로 롤백하지 않는다. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void verify(String token, String code, HttpServletRequest http) {
        Access a = byToken(token);
        Recipient r = a.recipient();
        Instant now = clock.instant();
        if (r.getOtpHash() == null || r.getOtpExpiresAt() == null || !now.isBefore(r.getOtpExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증번호가 만료되었습니다. 다시 받아 주세요.");
        }
        if (!Tokens.equalsConstantTime(r.getOtpHash(), otpHash(r, code))) {
            r.recordOtpFailure();
            audit.record(a.document().getId(), r.getId(), r.getEmail(), "OTP_FAILED", null);
            if (r.getOtpAttempts() >= OTP_MAX_ATTEMPTS) {
                r.clearOtp();
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "인증번호를 여러 번 틀렸습니다. 새 인증번호를 받아 주세요.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "인증번호가 맞지 않습니다. (남은 시도 " + (OTP_MAX_ATTEMPTS - r.getOtpAttempts()) + "번)");
        }
        r.clearOtp();
        audit.record(a.document().getId(), r.getId(), r.getEmail(), "OTP_VERIFIED", null);
        http.getSession(true);
        http.changeSessionId();
        http.getSession(false).setAttribute(RecipientSession.ATTR,
            new RecipientSession(r.getId(), a.document().getId(), now));
    }

    public void end(HttpSession session) {
        if (session != null) {
            session.removeAttribute(RecipientSession.ATTR);
        }
    }

    /** 세션의 수신자를 찾고, 지금도 접근할 수 있는지 확인한다. */
    @Transactional
    public Access current(HttpSession session) {
        RecipientSession s = session == null ? null : (RecipientSession) session.getAttribute(RecipientSession.ATTR);
        if (s == null || !isFresh(s)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "본인 확인이 필요합니다. 메일의 링크를 다시 열어 주세요.");
        }
        Recipient r = recipients.findById(s.recipientId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Document d = documents.findById(r.getDocumentId()).orElseThrow();
        checkAccessible(r, d);
        return new Access(r, d);
    }

    // ---------- 내부 ----------

    private Access byToken(String token) {
        Recipient r = recipients.findByAccessTokenHash(Tokens.sha256(token))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "링크가 올바르지 않거나 만료되었습니다."));
        Document d = documents.findById(r.getDocumentId()).orElseThrow();
        checkAccessible(r, d);
        return new Access(r, d);
    }

    private void checkAccessible(Recipient r, Document d) {
        Instant now = clock.instant();
        expiry.expireIfDue(d, now);
        switch (d.getStatus()) {
            case IN_PROGRESS -> {
                if (!r.isSigner()) {
                    throw gone("문서가 완료되면 완료본 링크를 보내 드립니다.");
                }
                if (r.getStatus() == RecipientStatus.WAITING) {
                    throw gone("아직 서명할 차례가 아닙니다. 차례가 되면 메일로 알려 드립니다.");
                }
            }
            case COMPLETED -> {
                if (d.getCompletedAt() != null && now.isAfter(d.getCompletedAt().plus(RESULT_ACCESS))) {
                    throw gone("완료 문서 열람 기간(30일)이 지났습니다. 요청자에게 문의해 주세요.");
                }
            }
            case RECALLED -> throw gone("요청자가 회수한 문서입니다.");
            case REJECTED -> throw gone("서명자가 반려해 종료된 문서입니다.");
            case EXPIRED -> throw gone("서명 요청 기간이 지난 문서입니다.");
        }
    }

    private boolean isFresh(RecipientSession s) {
        return clock.instant().isBefore(s.verifiedAt().plus(SESSION_TTL));
    }

    /** 수신자마다 다른 값을 섞어 같은 번호라도 해시가 달라지게 한다. */
    private static String otpHash(Recipient r, String code) {
        return Tokens.sha256(r.getPublicId() + ":" + code);
    }

    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String shown = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return shown + "***" + email.substring(at);
    }

    private static ResponseStatusException gone(String message) {
        return new ResponseStatusException(HttpStatus.GONE, message);
    }
}
