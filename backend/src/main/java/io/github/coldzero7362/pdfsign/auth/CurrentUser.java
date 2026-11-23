package io.github.coldzero7362.pdfsign.auth;

import io.github.coldzero7362.pdfsign.user.AppUser;
import io.github.coldzero7362.pdfsign.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 세션(Google)이든 API 토큰이든 현재 요청자의 계정을 찾는다. */
@Component
public class CurrentUser {

    private final AppUserRepository users;

    public CurrentUser(AppUserRepository users) {
        this.users = users;
    }

    public AppUser require(Authentication auth) {
        if (auth != null) {
            Object principal = auth.getPrincipal();
            if (principal instanceof OidcUser oidc) {
                return users.findByGoogleSub(oidc.getSubject()).orElseThrow(CurrentUser::unauthorized);
            }
            if (principal instanceof OAuth2AuthenticatedPrincipal token
                && token.getAttribute(ApiTokenIntrospector.ATTR_USER_ID) instanceof Long userId) {
                return users.findById(userId).orElseThrow(CurrentUser::unauthorized);
            }
        }
        throw unauthorized();
    }

    public static boolean isSession(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> Authorities.SESSION.equals(a.getAuthority()));
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
}
