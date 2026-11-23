package io.github.coldzero7362.pdfsign.auth;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API 토큰 관리. 보안상 브라우저 세션에서만 쓸 수 있다 (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/me/tokens")
public class ApiTokenController {

    private final ApiTokenService service;
    private final CurrentUser currentUser;
    private final Clock clock;

    public ApiTokenController(ApiTokenService service, CurrentUser currentUser, Clock clock) {
        this.service = service;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public record CreateRequest(
        @NotBlank @Size(max = 50) String name,
        @NotEmpty Set<String> scopes,
        Integer expiresInDays) {
    }

    public record TokenView(UUID id, String name, String prefix, Set<String> scopes,
                            Instant createdAt, Instant expiresAt, Instant lastUsedAt,
                            Instant revokedAt, boolean active) {
    }

    /** 발급 응답. token 원문은 이때 한 번만 내려준다. */
    public record CreatedResponse(TokenView info, String token) {
    }

    @Operation(summary = "내 API 토큰 목록")
    @GetMapping
    public List<TokenView> list(Authentication auth) {
        Long userId = currentUser.require(auth).getId();
        Instant now = clock.instant();
        return service.list(userId).stream().map(t -> view(t, now)).toList();
    }

    @Operation(summary = "API 토큰 발급", description = "응답의 token 값은 다시 볼 수 없으니 바로 보관하세요.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedResponse create(Authentication auth, @Valid @RequestBody CreateRequest req) {
        Long userId = currentUser.require(auth).getId();
        ApiTokenService.Issued issued = service.issue(userId, req.name(), req.scopes(), req.expiresInDays());
        return new CreatedResponse(view(issued.token(), clock.instant()), issued.rawToken());
    }

    @Operation(summary = "API 토큰 폐기")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Authentication auth, @PathVariable UUID id) {
        service.revoke(currentUser.require(auth).getId(), id);
    }

    private static TokenView view(ApiToken t, Instant now) {
        return new TokenView(t.getPublicId(), t.getName(), t.getTokenPrefix(), t.scopeSet(),
            t.getCreatedAt(), t.getExpiresAt(), t.getLastUsedAt(), t.getRevokedAt(), t.isActive(now));
    }
}
