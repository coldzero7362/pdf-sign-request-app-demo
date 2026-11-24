package io.github.coldzero7362.pdfsign.document;

import io.github.coldzero7362.pdfsign.template.TemplateFieldType;
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

/** 문서 칸 (템플릿 칸 복사본 + 입력값). recipientId가 null이면 요청자 고정값 칸. */
@Entity
@Table(name = "document_fields")
public class DocumentField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "recipient_id")
    private Long recipientId;

    @Column(name = "slot_key", length = 30)
    private String slotKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TemplateFieldType type;

    @Column(name = "page_no", nullable = false)
    private int pageNo;

    @Column(nullable = false)
    private double x;

    @Column(nullable = false)
    private double y;

    @Column(nullable = false)
    private double width;

    @Column(nullable = false)
    private double height;

    @Column(nullable = false)
    private boolean required;

    @Column(length = 100)
    private String label;

    @Column(name = "fixed_value", length = 500)
    private String fixedValue;

    @Column(name = "text_value", length = 2000)
    private String textValue;

    @Column(name = "file_key", length = 100)
    private String fileKey;

    @Column(name = "filled_at")
    private Instant filledAt;

    protected DocumentField() {
    }

    public DocumentField(Long documentId, Long recipientId, String slotKey, TemplateFieldType type, int pageNo,
                         double x, double y, double width, double height, boolean required, String label,
                         String fixedValue) {
        this.publicId = UUID.randomUUID();
        this.documentId = documentId;
        this.recipientId = recipientId;
        this.slotKey = slotKey;
        this.type = type;
        this.pageNo = pageNo;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.required = required;
        this.label = label;
        this.fixedValue = fixedValue;
    }

    public void fillText(String value, Instant now) {
        this.textValue = value;
        this.fileKey = null;
        this.filledAt = value == null ? null : now;
    }

    public void fillFile(String fileKey, Instant now) {
        this.fileKey = fileKey;
        this.textValue = null;
        this.filledAt = now;
    }

    /** 입력이 끝났는지. 고정값 칸은 늘 채워져 있다. */
    public boolean isFilled() {
        return switch (type) {
            case TEXT_FIXED -> true;
            case CHECKBOX -> "true".equals(textValue);
            case SIGNATURE -> fileKey != null;
            case TEXT, DATE -> textValue != null && !textValue.isBlank();
        };
    }

    /** PDF에 그릴 글자 (TEXT·DATE는 입력값, TEXT_FIXED는 고정값). */
    public String textToDraw() {
        return type == TemplateFieldType.TEXT_FIXED ? fixedValue : textValue;
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public Long getDocumentId() { return documentId; }
    public Long getRecipientId() { return recipientId; }
    public String getSlotKey() { return slotKey; }
    public TemplateFieldType getType() { return type; }
    public int getPageNo() { return pageNo; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public boolean isRequired() { return required; }
    public String getLabel() { return label; }
    public String getFixedValue() { return fixedValue; }
    public String getTextValue() { return textValue; }
    public String getFileKey() { return fileKey; }
    public Instant getFilledAt() { return filledAt; }
}
