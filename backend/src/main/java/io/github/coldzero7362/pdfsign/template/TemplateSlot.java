package io.github.coldzero7362.pdfsign.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 수신자 자리. 요청 JSON에서 slot_key마다 실제 서명자를 넣는다 (지침 11). */
@Entity
@Table(name = "template_slots")
public class TemplateSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "slot_key", nullable = false, length = 30)
    private String slotKey;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    protected TemplateSlot() {
    }

    public TemplateSlot(Long templateId, String slotKey, String label, int orderNo) {
        this.templateId = templateId;
        this.slotKey = slotKey;
        this.label = label;
        this.orderNo = orderNo;
    }

    public Long getId() { return id; }
    public Long getTemplateId() { return templateId; }
    public String getSlotKey() { return slotKey; }
    public String getLabel() { return label; }
    public int getOrderNo() { return orderNo; }
}
