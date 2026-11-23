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

/**
 * PDF 위의 입력 칸. 좌표는 페이지 왼쪽 위가 원점이고 단위는 PDF 포인트(1/72인치)다.
 * 회전된 페이지는 화면에 보이는 방향 기준이다.
 */
@Entity
@Table(name = "sign_fields")
public class SignField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private FieldType type;

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

    @Column(name = "text_value", length = 2000)
    private String textValue;

    @Column(name = "file_key", length = 100)
    private String fileKey;

    @Column(name = "filled_at")
    private Instant filledAt;

    protected SignField() {
    }

    public SignField(Long requestId, Long recipientId, FieldType type, int pageNo,
                     double x, double y, double width, double height, boolean required, String label) {
        this.publicId = UUID.randomUUID();
        this.requestId = requestId;
        this.recipientId = recipientId;
        this.type = type;
        this.pageNo = pageNo;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.required = required;
        this.label = label;
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public Long getRequestId() { return requestId; }
    public Long getRecipientId() { return recipientId; }
    public FieldType getType() { return type; }
    public int getPageNo() { return pageNo; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public boolean isRequired() { return required; }
    public String getLabel() { return label; }
    public String getTextValue() { return textValue; }
    public String getFileKey() { return fileKey; }
    public Instant getFilledAt() { return filledAt; }
}
