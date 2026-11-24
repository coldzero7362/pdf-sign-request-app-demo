package io.github.coldzero7362.pdfsign.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "recipient_id")
    private Long recipientId;

    /** 재발송으로 이어진 문서 (지침 13) */
    @Column(name = "related_document_id")
    private Long relatedDocumentId;

    @Column(nullable = false)
    private String actor;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(length = 1000)
    private String detail;

    @Column(length = 45)
    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(Long documentId, Long recipientId, Long relatedDocumentId, String actor, String eventType,
                      String detail, String ip, String userAgent, Instant createdAt) {
        this.documentId = documentId;
        this.recipientId = recipientId;
        this.relatedDocumentId = relatedDocumentId;
        this.actor = actor;
        this.eventType = eventType;
        this.detail = detail;
        this.ip = ip;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getDocumentId() { return documentId; }
    public Long getRelatedDocumentId() { return relatedDocumentId; }
    public Long getRecipientId() { return recipientId; }
    public String getActor() { return actor; }
    public String getEventType() { return eventType; }
    public String getDetail() { return detail; }
    public String getIp() { return ip; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
}
