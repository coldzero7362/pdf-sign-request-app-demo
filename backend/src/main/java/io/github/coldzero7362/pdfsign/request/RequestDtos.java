package io.github.coldzero7362.pdfsign.request;

import io.github.coldzero7362.pdfsign.document.DocumentController.DocumentView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 서명 요청 API의 요청·응답 형식. */
public final class RequestDtos {

    private RequestDtos() {
    }

    public record CreateRequest(
        @NotNull UUID documentId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String message,
        Instant expiresAt) {
    }

    public record UpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String message,
        Instant expiresAt) {
    }

    /**
     * 초안의 수신자와 입력 칸을 한 번에 저장한다 (편집 화면의 저장 단위).
     * key는 클라이언트가 정하는 임시 식별자로, 칸이 어느 수신자 것인지 잇는 데만 쓴다.
     */
    public record LayoutRequest(
        @NotNull @Size(max = 10) List<@Valid RecipientInput> recipients,
        @NotNull @Size(max = 200) List<@Valid FieldInput> fields) {
    }

    public record RecipientInput(
        @NotBlank @Size(max = 50) String key,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 100) String name,
        @NotNull RecipientRole role) {
    }

    public record FieldInput(
        @NotBlank @Size(max = 50) String recipientKey,
        @NotNull FieldType type,
        @Min(1) int page,
        double x,
        double y,
        double width,
        double height,
        boolean required,
        @Size(max = 100) String label) {
    }

    public record RecipientView(UUID id, String email, String name, RecipientRole role,
                                RecipientStatus status, int order, Instant completedAt,
                                Instant declinedAt, String declineReason) {
    }

    public record FieldView(UUID id, UUID recipientId, FieldType type, int page,
                            double x, double y, double width, double height,
                            boolean required, String label, String textValue,
                            boolean hasFile, Instant filledAt) {
    }

    public record RequestSummary(UUID id, String title, RequestStatus status, String documentName,
                                 int signerCount, int completedCount,
                                 Instant createdAt, Instant sentAt, Instant expiresAt) {
    }

    public record RequestDetail(UUID id, String title, String message, RequestStatus status,
                                Instant expiresAt, Instant createdAt, Instant updatedAt,
                                Instant sentAt, Instant completedAt, Instant canceledAt,
                                Instant declinedAt, String resultSha256, Long resultSizeBytes,
                                DocumentView document, List<RecipientView> recipients,
                                List<FieldView> fields) {
    }

    public record AuditView(Instant at, String type, String actor, String detail, String ip) {
    }
}
