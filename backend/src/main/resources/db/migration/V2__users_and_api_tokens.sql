-- 요청자 계정 (Google 로그인)
CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id     UUID         NOT NULL,
    google_sub    VARCHAR(64)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    last_login_at DATETIME(6)  NOT NULL,
    CONSTRAINT uk_users_public_id UNIQUE (public_id),
    CONSTRAINT uk_users_google_sub UNIQUE (google_sub)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 개인 API 토큰 (PAT). 토큰 원문은 저장하지 않고 SHA-256 해시만 저장한다.
CREATE TABLE api_tokens (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id    UUID         NOT NULL,
    user_id      BIGINT       NOT NULL,
    name         VARCHAR(50)  NOT NULL,
    token_prefix VARCHAR(12)  NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    scopes       VARCHAR(255) NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    expires_at   DATETIME(6)  NOT NULL,
    last_used_at DATETIME(6)  NULL,
    revoked_at   DATETIME(6)  NULL,
    CONSTRAINT uk_api_tokens_public_id UNIQUE (public_id),
    CONSTRAINT uk_api_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_api_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_api_tokens_user ON api_tokens (user_id);
