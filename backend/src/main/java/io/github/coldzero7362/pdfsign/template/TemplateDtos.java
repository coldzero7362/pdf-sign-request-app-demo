package io.github.coldzero7362.pdfsign.template;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TemplateDtos {

    private TemplateDtos() {
    }

    /** 템플릿 저장 (웹 편집기의 저장 단위). 자리와 칸은 통째로 바뀐다. */
    public record SaveRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull SigningMode signingMode,
        @Min(1) @Max(Template.MAX_PERIOD_DAYS) int defaultPeriodDays,
        @NotNull @Size(min = 1, max = 10) List<@Valid SlotInput> slots,
        @NotNull @Size(max = 300) List<@Valid FieldInput> fields) {
    }

    public record SlotInput(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,29}") String key,
        @NotBlank @Size(max = 50) String label) {
    }

    public record FieldInput(
        @Size(max = 30) String slotKey,
        @NotNull TemplateFieldType type,
        @Min(1) int page,
        double x,
        double y,
        double width,
        double height,
        /** 생략하면 false. 고정 텍스트 칸에는 의미가 없다 */
        Boolean required,
        @Size(max = 100) String label,
        @Size(max = 500) String fixedValue) {

        public boolean isRequired() {
            return Boolean.TRUE.equals(required);
        }
    }

    public record PageView(int pageNo, double width, double height, int rotation) {
    }

    public record PdfView(String fileName, long sizeBytes, String sha256, int pageCount, List<PageView> pages) {
    }

    public record SlotView(String key, String label, int order, int fieldCount) {
    }

    public record FieldView(UUID id, String slotKey, TemplateFieldType type, int page, double x, double y,
                            double width, double height, boolean required, String label, String fixedValue) {
    }

    public record TemplateView(String templateNo, String name, SigningMode signingMode, int defaultPeriodDays,
                               int revision, boolean ready, List<String> problems, PdfView pdf,
                               List<SlotView> slots, List<FieldView> fields,
                               Map<String, Object> requestExample, Instant createdAt, Instant updatedAt) {
    }

    public record TemplateSummary(String templateNo, String name, SigningMode signingMode, int defaultPeriodDays,
                                  int slotCount, int fieldCount, boolean ready, int revision,
                                  Instant createdAt, Instant updatedAt) {
    }
}
