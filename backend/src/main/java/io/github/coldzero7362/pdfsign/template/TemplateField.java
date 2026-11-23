package io.github.coldzero7362.pdfsign.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 템플릿 칸. 좌표는 페이지 왼쪽 위 원점, PDF 포인트 (회전 반영). */
@Entity
@Table(name = "template_fields")
public class TemplateField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    /** 채울 수신자 자리. 요청자 고정값(TEXT_FIXED)이면 null */
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

    protected TemplateField() {
    }

    public TemplateField(Long templateId, String slotKey, TemplateFieldType type, int pageNo, double x, double y,
                         double width, double height, boolean required, String label, String fixedValue) {
        this.publicId = UUID.randomUUID();
        this.templateId = templateId;
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

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public Long getTemplateId() { return templateId; }
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
}
