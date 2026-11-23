package io.github.coldzero7362.pdfsign.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {

    Optional<ApiToken> findByTokenHash(String tokenHash);

    Optional<ApiToken> findByPublicIdAndUserId(UUID publicId, Long userId);

    List<ApiToken> findByUserIdOrderByCreatedAtDesc(Long userId);
}
