package io.github.coldzero7362.pdfsign.auth;

import io.github.coldzero7362.pdfsign.user.AppUser;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final CurrentUser currentUser;

    public MeController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    public record MeResponse(UUID id, String email, String name, String authType, List<String> scopes) {
    }

    @Operation(summary = "현재 사용자", description = "세션 또는 API 토큰으로 인증된 사용자 정보")
    @GetMapping
    public MeResponse me(Authentication auth) {
        AppUser user = currentUser.require(auth);
        List<String> scopes = auth.getAuthorities().stream()
            .map(a -> a.getAuthority())
            .filter(a -> a.startsWith("SCOPE_"))
            .map(a -> a.substring("SCOPE_".length()))
            .sorted()
            .toList();
        return new MeResponse(user.getPublicId(), user.getEmail(), user.getName(),
            CurrentUser.isSession(auth) ? "session" : "token", scopes);
    }
}
