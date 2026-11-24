package io.github.coldzero7362.pdfsign.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 문서 수신자. 계정 없이 메일 링크 토큰 + 이메일 인증번호로 들어온다. */
@Entity
@Table(name = "document_recipients")
public class Recipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private RecipientRole role;

    @Column(name = "slot_key", length = 30)
    private String slotKey;

    @Column(name = "slot_label", length = 50)
    private String slotLabel;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private RecipientStatus status;

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

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "viewed_at")
    private Instant viewedAt;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    protected Recipient() {
    }

    public static Recipient signer(Long documentId, String slotKey, String slotLabel, int orderNo,
                                   String email, String name) {
        Recipient r = base(documentId, RecipientRole.SIGNER, orderNo, email, name);
        r.slotKey = slotKey;
        r.slotLabel = slotLabel;
        return r;
    }

    public static Recipient viewer(Long documentId, int orderNo, String email, String name) {
        return base(documentId, RecipientRole.VIEWER, orderNo, email, name);
    }

    public static Recipient owner(Long documentId, String email, String name) {
        return base(documentId, RecipientRole.OWNER, 0, email, name);
    }

    private static Recipient base(Long documentId, RecipientRole role, int orderNo, String email, String name) {
        Recipient r = new Recipient();
        r.publicId = UUID.randomUUID();
        r.documentId = documentId;
        r.role = role;
        r.orderNo = orderNo;
        r.email = email;
        r.name = name;
        r.status = RecipientStatus.WAITING;
        return r;
    }

    // ---------- 진행 ----------

    /** 서명 요청 메일을 보낼 차례가 됨. 링크 토큰(해시)을 정한다. */
    public void request(String tokenHash, Instant now) {
        this.status = RecipientStatus.REQUESTED;
        this.accessTokenHash = tokenHash;
        this.requestedAt = now;
    }

    /** 완료 문서 링크 발급 (참조자·요청자는 NOTIFIED로, 서명자는 상태 유지). */
    public void issueResultLink(String tokenHash) {
        this.accessTokenHash = tokenHash;
        if (role != RecipientRole.SIGNER) {
            this.status = RecipientStatus.NOTIFIED;
        }
    }

    /** 링크만 새로 발급 (메일 다시 보내기). */
    public void rotateAccessToken(String tokenHash) {
        this.accessTokenHash = tokenHash;
    }

    public void markViewed(Instant now) {
        if (viewedAt == null) {
            this.viewedAt = now;
        }
        if (status == RecipientStatus.REQUESTED) {
            this.status = RecipientStatus.VIEWED;
        }
    }

    public void sign(Instant now) {
        this.status = RecipientStatus.SIGNED;
        this.signedAt = now;
    }

    public void reject(String reason, Instant now) {
        this.status = RecipientStatus.REJECTED;
        this.rejectedAt = now;
        this.rejectReason = reason;
    }

    public boolean isSigner() {
        return role == RecipientRole.SIGNER;
    }

    /** 지금 서명할 수 있는 상태인지 (차례가 왔고 아직 끝내지 않음). */
    public boolean canSign() {
        return isSigner() && (status == RecipientStatus.REQUESTED || status == RecipientStatus.VIEWED);
    }

    // ---------- 인증번호 ----------

    public void issueOtp(String otpHash, Instant now, Instant expiresAt, Duration window) {
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

    public int otpSendsInWindow(Instant now, Duration window) {
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

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public Long getDocumentId() { return documentId; }
    public RecipientRole getRole() { return role; }
    public String getSlotKey() { return slotKey; }
    public String getSlotLabel() { return slotLabel; }
    public int getOrderNo() { return orderNo; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public RecipientStatus getStatus() { return status; }
    public String getAccessTokenHash() { return accessTokenHash; }
    public String getOtpHash() { return otpHash; }
    public Instant getOtpExpiresAt() { return otpExpiresAt; }
    public int getOtpAttempts() { return otpAttempts; }
    public Instant getOtpLastSentAt() { return otpLastSentAt; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getViewedAt() { return viewedAt; }
    public Instant getSignedAt() { return signedAt; }
    public Instant getRejectedAt() { return rejectedAt; }
    public String getRejectReason() { return rejectReason; }
}
