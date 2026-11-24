package io.github.coldzero7362.pdfsign.document;

import io.github.coldzero7362.pdfsign.template.SigningMode;
import io.github.coldzero7362.pdfsign.template.Template;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 문서: 템플릿으로 만든 서명 요청 한 건. 템플릿 내용은 발송 때 복사해 둔다. */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "template_no", nullable = false, length = 30)
    private String templateNo;

    @Column(name = "template_revision", nullable = false)
    private int templateRevision;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "signing_mode", nullable = false, length = 20)
    private SigningMode signingMode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "period_days", nullable = false)
    private int periodDays;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "recalled_at")
    private Instant recalledAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    @Column(name = "previous_document_id")
    private Long previousDocumentId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "pdf_storage_key", nullable = false, length = 100)
    private String pdfStorageKey;

    @Column(name = "pdf_file_name", nullable = false)
    private String pdfFileName;

    @Column(name = "pdf_size_bytes", nullable = false)
    private long pdfSizeBytes;

    @Column(name = "pdf_sha256", nullable = false, length = 64)
    private String pdfSha256;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "result_storage_key", length = 100)
    private String resultStorageKey;

    @Column(name = "result_sha256", length = 64)
    private String resultSha256;

    @Column(name = "result_size_bytes")
    private Long resultSizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "document_pages", joinColumns = @JoinColumn(name = "document_id"))
    @OrderBy("pageNo")
    private List<DocPage> pages = new ArrayList<>();

    protected Document() {
    }

    /** 템플릿을 복사해 문서를 만든다. */
    public static Document fromTemplate(Template t, String title, String message, int periodDays,
                                        Long previousDocumentId, String idempotencyKey, Instant now) {
        Document d = new Document();
        d.publicId = UUID.randomUUID();
        d.ownerId = t.getOwnerId();
        d.templateId = t.getId();
        d.templateNo = t.getTemplateNo();
        d.templateRevision = t.getRevision();
        d.title = title;
        d.message = message;
        d.signingMode = t.getSigningMode();
        d.status = DocumentStatus.IN_PROGRESS;
        d.periodDays = periodDays;
        d.expiresAt = now.plus(Duration.ofDays(periodDays));
        d.sentAt = now;
        d.previousDocumentId = previousDocumentId;
        d.idempotencyKey = idempotencyKey;
        d.pdfStorageKey = t.getPdfStorageKey();
        d.pdfFileName = t.getPdfFileName();
        d.pdfSizeBytes = t.getPdfSizeBytes();
        d.pdfSha256 = t.getPdfSha256();
        d.pageCount = t.getPageCount();
        d.pages = new ArrayList<>(t.getPages().stream()
            .map(p -> new DocPage(p.getPageNo(), p.getWidth(), p.getHeight(), p.getRotation())).toList());
        d.createdAt = now;
        d.updatedAt = now;
        return d;
    }

    // ---------- 상태 전환 ----------

    public boolean isPastExpiry(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void complete(String resultKey, String resultSha256, long resultSize, Instant now) {
        requireOpen();
        this.status = DocumentStatus.COMPLETED;
        this.resultStorageKey = resultKey;
        this.resultSha256 = resultSha256;
        this.resultSizeBytes = resultSize;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void reject(Instant now) {
        requireOpen();
        this.status = DocumentStatus.REJECTED;
        this.rejectedAt = now;
        this.updatedAt = now;
    }

    /** 회수: 진행 중이고 서명 요청 기간 안일 때만 (지침 6). */
    public void recall(Instant now) {
        if (!status.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "종료된 문서는 회수할 수 없습니다.");
        }
        if (isPastExpiry(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "서명 요청 기간이 지나 회수할 수 없습니다.");
        }
        this.status = DocumentStatus.RECALLED;
        this.recalledAt = now;
        this.updatedAt = now;
    }

    public void expire(Instant now) {
        if (status.isOpen()) {
            this.status = DocumentStatus.EXPIRED;
            this.expiredAt = now;
            this.updatedAt = now;
        }
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    private void requireOpen() {
        if (!status.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 종료된 문서입니다.");
        }
    }

    public DocPage page(int pageNo) {
        return pages.stream().filter(p -> p.getPageNo() == pageNo).findFirst().orElseThrow();
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public Long getOwnerId() { return ownerId; }
    public Long getTemplateId() { return templateId; }
    public String getTemplateNo() { return templateNo; }
    public int getTemplateRevision() { return templateRevision; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public SigningMode getSigningMode() { return signingMode; }
    public DocumentStatus getStatus() { return status; }
    public int getPeriodDays() { return periodDays; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getRejectedAt() { return rejectedAt; }
    public Instant getRecalledAt() { return recalledAt; }
    public Instant getExpiredAt() { return expiredAt; }
    public Instant getHiddenAt() { return hiddenAt; }
    public Long getPreviousDocumentId() { return previousDocumentId; }
    public String getPdfStorageKey() { return pdfStorageKey; }
    public String getPdfFileName() { return pdfFileName; }
    public long getPdfSizeBytes() { return pdfSizeBytes; }
    public String getPdfSha256() { return pdfSha256; }
    public int getPageCount() { return pageCount; }
    public String getResultStorageKey() { return resultStorageKey; }
    public String getResultSha256() { return resultSha256; }
    public Long getResultSizeBytes() { return resultSizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<DocPage> getPages() { return pages; }
}
