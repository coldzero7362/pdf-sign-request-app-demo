package io.github.coldzero7362.pdfsign.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;

/**
 * Bearer 토큰을 DB의 API 토큰으로 검증한다.
 * Spring Security의 opaque token 방식에 맞춰 두어, 나중에 OAuth 인증 서버를 붙이면
 * 이 클래스만 JWT 검증으로 바꾸면 된다.
 */
@Component
public class ApiTokenIntrospector implements OpaqueTokenIntrospector {

    public static final String ATTR_USER_ID = "user_id";
    public static final String ATTR_TOKEN_ID = "token_id";

    private final ApiTokenService tokens;

    public ApiTokenIntrospector(ApiTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        ApiToken apiToken = tokens.authenticate(token)
            .orElseThrow(() -> new BadOpaqueTokenException("유효하지 않거나 만료된 토큰입니다."));

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(Authorities.USER));
        authorities.add(new SimpleGrantedAuthority(Authorities.TOKEN));
        apiToken.scopeSet().forEach(s -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + s)));

        return new DefaultOAuth2AuthenticatedPrincipal(
            apiToken.getPublicId().toString(),
            Map.of(ATTR_USER_ID, apiToken.getUserId(),
                   ATTR_TOKEN_ID, apiToken.getPublicId().toString(),
                   "scope", String.join(" ", apiToken.scopeSet())),
            authorities);
    }
}
