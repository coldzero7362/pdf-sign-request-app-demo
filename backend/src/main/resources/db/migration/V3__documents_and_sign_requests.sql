-- 업로드한 원본 PDF
CREATE TABLE documents (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id   UUID         NOT NULL,
    owner_id    BIGINT       NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    storage_key VARCHAR(100) NOT NULL,
    size_bytes  BIGINT       NOT NULL,
    sha256      VARCHAR(64)  NOT NULL,
    page_count  INT          NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT uk_documents_public_id UNIQUE (public_id),
    CONSTRAINT fk_documents_owner FOREIGN KEY (owner_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 페이지 크기 (PDF 포인트, 회전 반영 후 화면에 보이는 방향 기준)
CREATE TABLE document_pages (
    document_id BIGINT NOT NULL,
    page_no     INT    NOT NULL,
    width_pt    DOUBLE NOT NULL,
    height_pt   DOUBLE NOT NULL,
    rotation    INT    NOT NULL,
    PRIMARY KEY (document_id, page_no),
    CONSTRAINT fk_document_pages_document FOREIGN KEY (document_id) REFERENCES documents (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 서명 요청
CREATE TABLE sign_requests (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id    UUID          NOT NULL,
    owner_id     BIGINT        NOT NULL,
    document_id  BIGINT        NOT NULL,
    title        VARCHAR(200)  NOT NULL,
    message      VARCHAR(2000) NULL,
    status       VARCHAR(20)   NOT NULL,
    expires_at   DATETIME(6)   NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    sent_at      DATETIME(6)   NULL,
    completed_at DATETIME(6)   NULL,
    canceled_at  DATETIME(6)   NULL,
    version      BIGINT        NOT NULL,
    CONSTRAINT uk_sign_requests_public_id UNIQUE (public_id),
    CONSTRAINT fk_sign_requests_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_sign_requests_document FOREIGN KEY (document_id) REFERENCES documents (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_sign_requests_owner ON sign_requests (owner_id, created_at);

-- 수신자 (외부 사용자, 계정 없음)
CREATE TABLE recipients (
    id             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id      UUID          NOT NULL,
    request_id     BIGINT        NOT NULL,
    email          VARCHAR(255)  NOT NULL,
    name           VARCHAR(100)  NOT NULL,
    role           VARCHAR(10)   NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    sort_order     INT           NOT NULL,
    completed_at   DATETIME(6)   NULL,
    declined_at    DATETIME(6)   NULL,
    decline_reason VARCHAR(500)  NULL,
    CONSTRAINT uk_recipients_public_id UNIQUE (public_id),
    CONSTRAINT fk_recipients_request FOREIGN KEY (request_id) REFERENCES sign_requests (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_recipients_request ON recipients (request_id);

-- 입력 칸과 입력값. 좌표는 페이지 왼쪽 위가 원점인 PDF 포인트.
CREATE TABLE sign_fields (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id    UUID          NOT NULL,
    request_id   BIGINT        NOT NULL,
    recipient_id BIGINT        NOT NULL,
    type         VARCHAR(20)   NOT NULL,
    page_no      INT           NOT NULL,
    x            DOUBLE        NOT NULL,
    y            DOUBLE        NOT NULL,
    width        DOUBLE        NOT NULL,
    height       DOUBLE        NOT NULL,
    required     BOOLEAN       NOT NULL,
    label        VARCHAR(100)  NULL,
    text_value   VARCHAR(2000) NULL,
    file_key     VARCHAR(100)  NULL,
    filled_at    DATETIME(6)   NULL,
    CONSTRAINT uk_sign_fields_public_id UNIQUE (public_id),
    CONSTRAINT fk_sign_fields_request FOREIGN KEY (request_id) REFERENCES sign_requests (id),
    CONSTRAINT fk_sign_fields_recipient FOREIGN KEY (recipient_id) REFERENCES recipients (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_sign_fields_request ON sign_fields (request_id);

-- 감사 기록 (누가, 언제, 어디서, 무엇을)
CREATE TABLE audit_events (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id   BIGINT        NOT NULL,
    recipient_id BIGINT        NULL,
    actor        VARCHAR(255)  NOT NULL,
    event_type   VARCHAR(30)   NOT NULL,
    detail       VARCHAR(1000) NULL,
    ip           VARCHAR(45)   NULL,
    user_agent   VARCHAR(255)  NULL,
    created_at   DATETIME(6)   NOT NULL,
    CONSTRAINT fk_audit_events_request FOREIGN KEY (request_id) REFERENCES sign_requests (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_audit_events_request ON audit_events (request_id, created_at);
