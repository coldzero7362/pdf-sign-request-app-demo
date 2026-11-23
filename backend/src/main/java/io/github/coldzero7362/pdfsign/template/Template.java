package io.github.coldzero7362.pdfsign.template;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 서명 요청 양식. 요청자가 웹에서 만들고, API로 서명을 요청할 때 번호로 고른다. */
@Entity
@Table(name = "templates")
public class Template {

    public static final int DEFAULT_PERIOD_DAYS = 14;
    public static final int MAX_PERIOD_DAYS = 31;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "template_no", nullable = false, length = 30)
    private String templateNo;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "signing_mode", nullable = false, length = 20)
    private SigningMode signingMode;

    @Column(name = "default_period_days", nullable = false)
    private int defaultPeriodDays;

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

    /** 내용을 저장할 때마다 1씩 오른다. 문서에 "어느 판으로 보냈는지" 남길 때 쓴다. */
    @Column(nullable = false)
    private int revision;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "template_pages", joinColumns = @JoinColumn(name = "template_id"))
    @OrderBy("pageNo")
    private List<TemplatePage> pages = new ArrayList<>();

    protected Template() {
    }

    public Template(Long ownerId, String templateNo, String name, String pdfStorageKey, String pdfFileName,
                    long pdfSizeBytes, String pdfSha256, List<TemplatePage> pages, Instant now) {
        this.publicId = UUID.randomUUID();
        this.ownerId = ownerId;
        this.templateNo = templateNo;
        this.name = name;
        this.signingMode = SigningMode.SEQUENTIAL;
        this.defaultPeriodDays = DEFAULT_PERIOD_DAYS;
        this.pdfStorageKey = pdfStorageKey;
        this.pdfFileName = pdfFileName;
        this.pdfSizeBytes = pdfSizeBytes;
        this.pdfSha256 = pdfSha256;
        this.pageCount = pages.size();
        this.pages = new ArrayList<>(pages);
        this.revision = 1;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateSettings(String name, SigningMode mode, int periodDays, Instant now) {
        this.name = name;
        this.signingMode = mode;
        this.defaultPeriodDays = periodDays;
        this.revision++;
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public Optional<TemplatePage> page(int pageNo) {
        return pages.stream().filter(p -> p.getPageNo() == pageNo).findFirst();
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public Long getOwnerId() { return ownerId; }
    public String getTemplateNo() { return templateNo; }
    public String getName() { return name; }
    public SigningMode getSigningMode() { return signingMode; }
    public int getDefaultPeriodDays() { return defaultPeriodDays; }
    public String getPdfStorageKey() { return pdfStorageKey; }
    public String getPdfFileName() { return pdfFileName; }
    public long getPdfSizeBytes() { return pdfSizeBytes; }
    public String getPdfSha256() { return pdfSha256; }
    public int getPageCount() { return pageCount; }
    public int getRevision() { return revision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public List<TemplatePage> getPages() { return pages; }
}
