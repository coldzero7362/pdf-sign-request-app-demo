package io.github.coldzero7362.pdfsign.document;

import io.github.coldzero7362.pdfsign.template.SigningMode;
import io.github.coldzero7362.pdfsign.template.TemplateFieldType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 문서 API 형식 (지침 4, 11). */
public final class DocumentDtos {

    private DocumentDtos() {
    }

    /** 서명 요청. signers는 템플릿의 모든 자리를 채워야 한다. viewers는 순서 없이 자유롭게. */
    public record CreateRequest(
        @NotBlank @Size(max = 30) String templateNo,
        @Size(max = 200) String title,
        @Size(max = 2000) String message,
        @Min(1) @Max(31) Integer periodDays,
        @NotNull @Size(min = 1, max = 10) List<@Valid SignerInput> signers,
        @Size(max = 20) List<@Valid ViewerInput> viewers) {
    }

    public record SignerInput(
        @NotBlank @Size(max = 30) String slot,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 100) String name) {
    }

    public record ViewerInput(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 100) String name) {
    }

    public record RecipientView(UUID id, String role, String slot, String slotLabel, int order, String email,
                                String name, RecipientStatus status, Instant requestedAt, Instant viewedAt,
                                Instant signedAt, Instant rejectedAt, String rejectReason) {
    }

    public record FieldView(UUID id, String slot, TemplateFieldType type, int page, double x, double y,
                            double width, double height, boolean required, String label, String fixedValue,
                            String value, boolean hasFile, Instant filledAt) {
    }

    public record PageView(int pageNo, double width, double height, int rotation) {
    }

    public record AuditView(Instant at, String type, String actor, String detail, String ip,
                            UUID relatedDocumentId) {
    }

    /** 재발송으로 이어진 문서 (지침 13, 19) */
    public record LinkedDocument(UUID id, String title, DocumentStatus status, String relation) {
    }

    public record DocumentSummary(UUID id, String title, DocumentStatus status, String templateNo,
                                  SigningMode signingMode, int signerCount, int signedCount, String currentSigner,
                                  Instant sentAt, Instant expiresAt, Instant completedAt, boolean hidden) {
    }

    public record DocumentDetail(UUID id, String title, String message, DocumentStatus status, String templateNo,
                                 int templateRevision, SigningMode signingMode, int periodDays, Instant sentAt,
                                 Instant expiresAt, Instant completedAt, Instant rejectedAt, Instant recalledAt,
                                 Instant expiredAt, Instant hiddenAt, String pdfFileName, int pageCount,
                                 List<PageView> pages, String resultSha256, Long resultSizeBytes,
                                 List<RecipientView> signers, List<RecipientView> viewers,
                                 List<FieldView> fields, List<AuditView> audit, List<LinkedDocument> linked,
                                 boolean canRecall) {
    }
}
