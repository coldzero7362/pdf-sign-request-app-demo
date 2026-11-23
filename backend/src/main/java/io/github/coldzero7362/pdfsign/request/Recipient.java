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

    public void markSent() {
        this.status = RecipientStatus.SENT;
    }

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
