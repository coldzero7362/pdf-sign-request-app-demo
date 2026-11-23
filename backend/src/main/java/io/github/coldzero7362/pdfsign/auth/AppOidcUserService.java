package io.github.coldzero7362.pdfsign.auth;

import io.github.coldzero7362.pdfsign.user.AppUser;
import io.github.coldzero7362.pdfsign.user.AppUserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Google 로그인 성공 시 요청자 계정을 만들거나 갱신하고, 세션 권한을 붙인다. */
@Service
public class AppOidcUserService extends OidcUserService {

    private final AppUserRepository users;
    private final Clock clock;

    public AppOidcUserService(AppUserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser google = super.loadUser(request);

        if (!Boolean.TRUE.equals(google.getEmailVerified()) || !StringUtils.hasText(google.getEmail())) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_not_verified"),
                "Google 계정의 이메일이 인증되지 않았습니다.");
        }

        String name = StringUtils.hasText(google.getFullName()) ? google.getFullName() : google.getEmail();
        if (name.length() > 100) {
            name = name.substring(0, 100);
        }
        Instant now = clock.instant();
        String finalName = name;
        users.findByGoogleSub(google.getSubject()).ifPresentOrElse(
            user -> user.recordLogin(google.getEmail(), finalName, now),
            () -> users.save(new AppUser(google.getSubject(), google.getEmail(), finalName, now)));

        return new DefaultOidcUser(
            AuthorityUtils.createAuthorityList(Authorities.SESSION_AUTHORITIES),
            google.getIdToken(), google.getUserInfo(), IdTokenClaimNames.SUB);
    }
}
