package io.github.coldzero7362.pdfsign.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 요청을 받는 외부 사용자. 계정 없이 메일 링크와 OTP로 접근한다 (다음 단계). */
@Entity
@Table(name = "recipients")
public class Recipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private RecipientRole role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private RecipientStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "declined_at")
    private Instant declinedAt;

    @Column(name = "decline_reason", length = 500)
    private String declineReason;

    @Column(name = "access_token_hash", length = 64, unique = true)
    private String accessTokenHash;

    @Column(name = "otp_hash", length = 64)
    private String otpHash;

    @Column(name = "otp_expires_at")
    private Instant otpExpiresAt;

    @Column(name = "otp_attempts", nullable = false)
    private int otpAttempts;

    @Column(name = "otp_window_start")
    private Instant otpWindowStart;

    @Column(name = "otp_send_count", nullable = false)
    private int otpSendCount;

    @Column(name = "otp_last_sent_at")
    private Instant otpLastSentAt;

    @Column(name = "viewed_at")
    private Instant viewedAt;

    protected Recipient() {
    }

    public Recipient(Long requestId, String email, String name, RecipientRole role, int sortOrder) {
        this.publicId = UUID.randomUUID();
        this.requestId = requestId;
        this.email = email;
        this.name = name;
        this.role = role;
        this.status = RecipientStatus.PENDING;
        this.sortOrder = sortOrder;
    }

    /** 발송할 때 메일 링크 토큰(해시)을 정한다. */
    public void markSent(String accessTokenHash) {
        this.status = RecipientStatus.SENT;
        this.accessTokenHash = accessTokenHash;
    }

    /** 링크 토큰만 새로 바꾼다 (완료본 링크 발송용). 상태는 그대로 둔다. */
    public void rotateAccessToken(String accessTokenHash) {
        this.accessTokenHash = accessTokenHash;
    }

    public boolean isSigner() {
        return role == RecipientRole.SIGNER;
    }

    public boolean isDone() {
        return status == RecipientStatus.COMPLETED || status == RecipientStatus.DECLINED;
    }

    // ---------- OTP ----------

    /** 새 인증번호를 기록한다. 이전 번호와 틀린 횟수는 버린다. */
    public void issueOtp(String otpHash, Instant now, Instant expiresAt, java.time.Duration window) {
        if (otpWindowStart == null || !now.isBefore(otpWindowStart.plus(window))) {
            this.otpWindowStart = now;
            this.otpSendCount = 0;
        }
        this.otpSendCount++;
        this.otpHash = otpHash;
        this.otpExpiresAt = expiresAt;
        this.otpAttempts = 0;
        this.otpLastSentAt = now;
    }

    public int otpSendsInWindow(Instant now, java.time.Duration window) {
        return otpWindowStart == null || !now.isBefore(otpWindowStart.plus(window)) ? 0 : otpSendCount;
    }

    public void recordOtpFailure() {
        this.otpAttempts++;
    }

    public void clearOtp() {
        this.otpHash = null;
        this.otpExpiresAt = null;
        this.otpAttempts = 0;
    }

    public void markViewed(Instant now) {
        if (viewedAt == null) {
            this.viewedAt = now;
        }
        if (status == RecipientStatus.SENT) {
            this.status = RecipientStatus.VIEWED;
        }
    }

    public void complete(Instant now) {
        this.status = RecipientStatus.COMPLETED;
        this.completedAt = now;
    }

    public void decline(String reason, Instant now) {
        this.status = RecipientStatus.DECLINED;
        this.declinedAt = now;
        this.declineReason = reason;
    }

    public String getAccessTokenHash() { return accessTokenHash; }
    public String getOtpHash() { return otpHash; }
    public Instant getOtpExpiresAt() { return otpExpiresAt; }
    public int getOtpAttempts() { return otpAttempts; }
    public Instant getOtpLastSentAt() { return otpLastSentAt; }
    public Instant getViewedAt() { return viewedAt; }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public Long getRequestId() { return requestId; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public RecipientRole getRole() { return role; }
    public RecipientStatus getStatus() { return status; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getDeclinedAt() { return declinedAt; }
    public String getDeclineReason() { return declineReason; }
}
