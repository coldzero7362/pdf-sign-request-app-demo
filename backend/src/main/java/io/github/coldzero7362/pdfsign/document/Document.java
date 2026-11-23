package io.github.coldzero7362.pdfsign.document;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 업로드한 원본 PDF. 내용은 바뀌지 않는다. */
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

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "storage_key", nullable = false, length = 100)
    private String storageKey;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "document_pages", joinColumns = @JoinColumn(name = "document_id"))
    @OrderBy("pageNo")
    private List<DocumentPage> pages = new ArrayList<>();

    protected Document() {
    }

    public Document(Long ownerId, String fileName, String storageKey, long sizeBytes, String sha256,
                    List<DocumentPage> pages, Instant now) {
        this.publicId = UUID.randomUUID();
        this.ownerId = ownerId;
        this.fileName = fileName;
        this.storageKey = storageKey;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.pageCount = pages.size();
        this.pages = new ArrayList<>(pages);
        this.createdAt = now;
    }

    public Optional<DocumentPage> page(int pageNo) {
        return pages.stream().filter(p -> p.getPageNo() == pageNo).findFirst();
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public Long getOwnerId() { return ownerId; }
    public String getFileName() { return fileName; }
    public String getStorageKey() { return storageKey; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public int getPageCount() { return pageCount; }
    public Instant getCreatedAt() { return createdAt; }
    public List<DocumentPage> getPages() { return pages; }
}
