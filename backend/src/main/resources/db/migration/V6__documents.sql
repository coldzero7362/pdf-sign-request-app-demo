-- 옛 요청 구조를 지우고 템플릿 기반 "문서" 구조로 바꾼다 (테스트 데이터는 버리기로 결정, 계획 3.4)
DROP TABLE IF EXISTS sign_fields;
DROP TABLE IF EXISTS audit_events;
DROP TABLE IF EXISTS recipients;
DROP TABLE IF EXISTS sign_requests;
DROP TABLE IF EXISTS document_pages;
DROP TABLE IF EXISTS documents;

-- 문서: API로 만든 서명 요청 한 건. 발송할 때 템플릿 내용을 복사해 둔다 (템플릿을 고쳐도 영향 없음)
CREATE TABLE documents (
    id                   BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id            UUID          NOT NULL,
    owner_id             BIGINT        NOT NULL,
    template_id          BIGINT        NOT NULL,
    template_no          VARCHAR(30)   NOT NULL,
    template_revision    INT           NOT NULL,
    title                VARCHAR(200)  NOT NULL,
    message              VARCHAR(2000) NULL,
    signing_mode         VARCHAR(20)   NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    period_days          INT           NOT NULL,
    expires_at           DATETIME(6)   NOT NULL,
    sent_at              DATETIME(6)   NOT NULL,
    completed_at         DATETIME(6)   NULL,
    rejected_at          DATETIME(6)   NULL,
    recalled_at          DATETIME(6)   NULL,
    expired_at           DATETIME(6)   NULL,
    hidden_at            DATETIME(6)   NULL,
    previous_document_id BIGINT        NULL,
    idempotency_key      VARCHAR(100)  NULL,
    pdf_storage_key      VARCHAR(100)  NOT NULL,
    pdf_file_name        VARCHAR(255)  NOT NULL,
    pdf_size_bytes       BIGINT        NOT NULL,
    pdf_sha256           VARCHAR(64)   NOT NULL,
    page_count           INT           NOT NULL,
    result_storage_key   VARCHAR(100)  NULL,
    result_sha256        VARCHAR(64)   NULL,
    result_size_bytes    BIGINT        NULL,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    version              BIGINT        NOT NULL,
    CONSTRAINT uk_documents_public_id UNIQUE (public_id),
    CONSTRAINT uk_documents_idempotency UNIQUE (owner_id, idempotency_key),
    CONSTRAINT fk_documents_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_documents_template FOREIGN KEY (template_id) REFERENCES templates (id),
    CONSTRAINT fk_documents_previous FOREIGN KEY (previous_document_id) REFERENCES documents (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_documents_owner ON documents (owner_id, sent_at);

CREATE TABLE document_pages (
    document_id BIGINT NOT NULL,
    page_no     INT    NOT NULL,
    width_pt    DOUBLE NOT NULL,
    height_pt   DOUBLE NOT NULL,
    rotation    INT    NOT NULL,
    PRIMARY KEY (document_id, page_no),
    CONSTRAINT fk_document_pages_document FOREIGN KEY (document_id) REFERENCES documents (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 수신자: 서명자(SIGNER, 자리별), 참조자(VIEWER), 요청자(OWNER, 완료 링크용)
CREATE TABLE document_recipients (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id         UUID         NOT NULL,
    document_id       BIGINT       NOT NULL,
    role              VARCHAR(10)  NOT NULL,
    slot_key          VARCHAR(30)  NULL,
    slot_label        VARCHAR(50)  NULL,
    order_no          INT          NOT NULL,
    email             VARCHAR(255) NOT NULL,
    name              VARCHAR(100) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    access_token_hash VARCHAR(64)  NULL,
    otp_hash          VARCHAR(64)  NULL,
    otp_expires_at    DATETIME(6)  NULL,
    otp_attempts      INT          NOT NULL DEFAULT 0,
    otp_window_start  DATETIME(6)  NULL,
    otp_send_count    INT          NOT NULL DEFAULT 0,
    otp_last_sent_at  DATETIME(6)  NULL,
    requested_at      DATETIME(6)  NULL,
    viewed_at         DATETIME(6)  NULL,
    signed_at         DATETIME(6)  NULL,
    rejected_at       DATETIME(6)  NULL,
    reject_reason     VARCHAR(500) NULL,
    CONSTRAINT uk_document_recipients_public_id UNIQUE (public_id),
    CONSTRAINT uk_document_recipients_token UNIQUE (access_token_hash),
    CONSTRAINT fk_document_recipients_document FOREIGN KEY (document_id) REFERENCES documents (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_document_recipients_document ON document_recipients (document_id);

-- 칸 (템플릿 칸 복사본 + 입력값). recipient_id가 NULL이면 요청자 고정값 칸
CREATE TABLE document_fields (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id    UUID          NOT NULL,
    document_id  BIGINT        NOT NULL,
    recipient_id BIGINT        NULL,
    slot_key     VARCHAR(30)   NULL,
    type         VARCHAR(20)   NOT NULL,
    page_no      INT           NOT NULL,
    x            DOUBLE        NOT NULL,
    y            DOUBLE        NOT NULL,
    width        DOUBLE        NOT NULL,
    height       DOUBLE        NOT NULL,
    required     BOOLEAN       NOT NULL,
    label        VARCHAR(100)  NULL,
    fixed_value  VARCHAR(500)  NULL,
    text_value   VARCHAR(2000) NULL,
    file_key     VARCHAR(100)  NULL,
    filled_at    DATETIME(6)   NULL,
    CONSTRAINT uk_document_fields_public_id UNIQUE (public_id),
    CONSTRAINT fk_document_fields_document FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT fk_document_fields_recipient FOREIGN KEY (recipient_id) REFERENCES document_recipients (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_document_fields_document ON document_fields (document_id);

-- 감사 기록. related_document_id는 재발송으로 이어진 문서 (지침 13)
CREATE TABLE audit_events (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    document_id         BIGINT        NOT NULL,
    recipient_id        BIGINT        NULL,
    related_document_id BIGINT        NULL,
    actor               VARCHAR(255)  NOT NULL,
    event_type          VARCHAR(30)   NOT NULL,
    detail              VARCHAR(1000) NULL,
    ip                  VARCHAR(45)   NULL,
    user_agent          VARCHAR(255)  NULL,
    created_at          DATETIME(6)   NOT NULL,
    CONSTRAINT fk_audit_events_document FOREIGN KEY (document_id) REFERENCES documents (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_audit_events_document ON audit_events (document_id, created_at);
CREATE INDEX idx_audit_events_recipient ON audit_events (recipient_id, event_type, created_at);
