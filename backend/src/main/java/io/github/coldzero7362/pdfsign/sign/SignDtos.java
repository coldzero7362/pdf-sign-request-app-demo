package io.github.coldzero7362.pdfsign.sign;

import io.github.coldzero7362.pdfsign.document.DocumentController.PageView;
import io.github.coldzero7362.pdfsign.request.FieldType;
import io.github.coldzero7362.pdfsign.request.RecipientRole;
import io.github.coldzero7362.pdfsign.request.RecipientStatus;
import io.github.coldzero7362.pdfsign.request.RequestStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SignDtos {

    private SignDtos() {
    }

    public record TokenRequest(@NotBlank @Size(max = 100) String token) {
    }

    public record VerifyRequest(@NotBlank @Size(max = 100) String token,
                                @NotBlank @Pattern(regexp = "\\d{6}") String code) {
    }

    public record FillRequest(@Size(max = 500) String value) {
    }

    public record DeclineRequest(@Size(max = 500) String reason) {
    }

    /** 링크를 열었을 때 (인증 전) 보여 줄 최소 정보. 문서 내용은 주지 않는다. */
    public record LinkInfo(String title, String senderName, String maskedEmail, RecipientRole role,
                           RequestStatus requestStatus, boolean verified) {
    }

    public record OtpSent(String maskedEmail, int resendAfterSeconds) {
    }

    public record MyField(UUID id, FieldType type, int page, double x, double y, double width, double height,
                          boolean required, String label, String value, boolean hasImage, boolean filled) {
    }

    public record SigningView(String title, String message, String senderName, String senderEmail,
                              RequestStatus requestStatus, Instant expiresAt,
                              String recipientName, String recipientEmail, RecipientRole role,
                              RecipientStatus recipientStatus, boolean canEdit, boolean resultAvailable,
                              String documentName, List<PageView> pages, List<MyField> fields) {
    }
}
