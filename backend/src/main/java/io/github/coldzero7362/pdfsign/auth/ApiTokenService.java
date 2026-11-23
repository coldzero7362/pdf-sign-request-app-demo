package io.github.coldzero7362.pdfsign.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** API 토큰 발급, 조회, 폐기, 검증. */
@Service
public class ApiTokenService {

    /** 토큰 앞에 붙여 이 서비스의 토큰임을 알아보기 쉽게 한다 (비밀 스캐너 탐지용). */
    public static final String TOKEN_PREFIX = "pst_";
    static final int MAX_ACTIVE_TOKENS = 10;
    static final int DEFAULT_DAYS = 90;
    static final int MAX_DAYS = 365;

    private final ApiTokenRepository tokens;
    private final Clock clock;

    public ApiTokenService(ApiTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    public record Issued(ApiToken token, String rawToken) {
    }

    @Transactional
    public Issued issue(Long userId, String name, Set<String> scopes, Integer expiresInDays) {
        if (scopes == null || scopes.isEmpty() || !Authorities.ALL_SCOPES.containsAll(scopes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "허용되지 않은 스코프입니다: " + scopes);
        }
        int days = expiresInDays == null ? DEFAULT_DAYS : expiresInDays;
        if (days < 1 || days > MAX_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "만료 기간은 1~" + MAX_DAYS + "일입니다.");
        }
        Instant now = clock.instant();
        long active = tokens.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .filter(t -> t.isActive(now)).count();
        if (active >= MAX_ACTIVE_TOKENS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "사용 중인 토큰은 최대 " + MAX_ACTIVE_TOKENS + "개입니다. 안 쓰는 토큰을 폐기해 주세요.");
        }

        String raw = TOKEN_PREFIX + io.github.coldzero7362.pdfsign.common.Tokens.random();
        ApiToken token = new ApiToken(userId, name.strip(), raw.substring(0, 12), hash(raw),
            scopes, now, now.plus(Duration.ofDays(days)));
        return new Issued(tokens.save(token), raw);
    }

    @Transactional(readOnly = true)
    public List<ApiToken> list(Long userId) {
        return tokens.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void revoke(Long userId, UUID publicId) {
        ApiToken token = tokens.findByPublicIdAndUserId(publicId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        token.revoke(clock.instant());
    }

    /** Bearer 값을 검증해 사용 중인 토큰이면 돌려주고 마지막 사용 시각을 남긴다. */
    @Transactional
    public Optional<ApiToken> authenticate(String raw) {
        if (raw == null || !raw.startsWith(TOKEN_PREFIX) || raw.length() > 100) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return tokens.findByTokenHash(hash(raw))
            .filter(t -> t.isActive(now))
            .map(t -> {
                t.markUsed(now);
                return t;
            });
    }

    static String hash(String raw) {
        return io.github.coldzero7362.pdfsign.common.Tokens.sha256(raw);
    }
}
