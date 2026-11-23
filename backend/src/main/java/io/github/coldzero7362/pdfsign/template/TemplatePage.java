package io.github.coldzero7362.pdfsign.template;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class TemplatePage {

    @Column(name = "page_no", nullable = false)
    private int pageNo;

    @Column(name = "width_pt", nullable = false)
    private double width;

    @Column(name = "height_pt", nullable = false)
    private double height;

    @Column(nullable = false)
    private int rotation;

    protected TemplatePage() {
    }

    public TemplatePage(int pageNo, double width, double height, int rotation) {
        this.pageNo = pageNo;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
    }

    public int getPageNo() { return pageNo; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public int getRotation() { return rotation; }
}
