package io.github.coldzero7362.pdfsign.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Entity
@Table(name = "sign_requests")
public class SignRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private RequestStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected SignRequest() {
    }

    public SignRequest(Long ownerId, Long documentId, String title, String message, Instant expiresAt, Instant now) {
        this.publicId = UUID.randomUUID();
        this.ownerId = ownerId;
        this.documentId = documentId;
        this.title = title;
        this.message = message;
        this.expiresAt = expiresAt;
        this.status = RequestStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void requireDraft() {
        if (status != RequestStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "발송 전(초안) 상태에서만 수정할 수 있습니다.");
        }
    }

    public void update(String title, String message, Instant expiresAt, Instant now) {
        requireDraft();
        this.title = title;
        this.message = message;
        this.expiresAt = expiresAt;
        this.updatedAt = now;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public void markSent(Instant now) {
        requireDraft();
        this.status = RequestStatus.SENT;
        this.sentAt = now;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        if (!status.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "진행 중인 요청만 취소할 수 있습니다.");
        }
        this.status = RequestStatus.CANCELED;
        this.canceledAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public Long getOwnerId() { return ownerId; }
    public Long getDocumentId() { return documentId; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public RequestStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCanceledAt() { return canceledAt; }
    public long getVersion() { return version; }
}
