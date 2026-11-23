-- 템플릿 번호 순번 (요청자별)
ALTER TABLE users ADD COLUMN template_seq INT NOT NULL DEFAULT 0;

-- 템플릿: 요청자가 웹에서 만들어 두는 서명 요청 양식
CREATE TABLE templates (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id           UUID         NOT NULL,
    owner_id            BIGINT       NOT NULL,
    template_no         VARCHAR(30)  NOT NULL,
    name                VARCHAR(100) NOT NULL,
    signing_mode        VARCHAR(20)  NOT NULL,
    default_period_days INT          NOT NULL,
    pdf_storage_key     VARCHAR(100) NOT NULL,
    pdf_file_name       VARCHAR(255) NOT NULL,
    pdf_size_bytes      BIGINT       NOT NULL,
    pdf_sha256          VARCHAR(64)  NOT NULL,
    page_count          INT          NOT NULL,
    revision            INT          NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    deleted_at          DATETIME(6)  NULL,
    version             BIGINT       NOT NULL,
    CONSTRAINT uk_templates_public_id UNIQUE (public_id),
    CONSTRAINT uk_templates_owner_no UNIQUE (owner_id, template_no),
    CONSTRAINT fk_templates_owner FOREIGN KEY (owner_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE template_pages (
    template_id BIGINT NOT NULL,
    page_no     INT    NOT NULL,
    width_pt    DOUBLE NOT NULL,
    height_pt   DOUBLE NOT NULL,
    rotation    INT    NOT NULL,
    PRIMARY KEY (template_id, page_no),
    CONSTRAINT fk_template_pages_template FOREIGN KEY (template_id) REFERENCES templates (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 수신자 자리 ("서명자 1", "서명자 2" ...). 순차 진행이면 order_no 순서로 진행
CREATE TABLE template_slots (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT      NOT NULL,
    slot_key    VARCHAR(30) NOT NULL,
    label       VARCHAR(50) NOT NULL,
    order_no    INT         NOT NULL,
    CONSTRAINT uk_template_slots_key UNIQUE (template_id, slot_key),
    CONSTRAINT fk_template_slots_template FOREIGN KEY (template_id) REFERENCES templates (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 칸. slot_key가 NULL이면 요청자가 템플릿에서 값을 채워 두는 칸(TEXT_FIXED)
CREATE TABLE template_fields (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id   UUID         NOT NULL,
    template_id BIGINT       NOT NULL,
    slot_key    VARCHAR(30)  NULL,
    type        VARCHAR(20)  NOT NULL,
    page_no     INT          NOT NULL,
    x           DOUBLE       NOT NULL,
    y           DOUBLE       NOT NULL,
    width       DOUBLE       NOT NULL,
    height      DOUBLE       NOT NULL,
    required    BOOLEAN      NOT NULL,
    label       VARCHAR(100) NULL,
    fixed_value VARCHAR(500) NULL,
    CONSTRAINT uk_template_fields_public_id UNIQUE (public_id),
    CONSTRAINT fk_template_fields_template FOREIGN KEY (template_id) REFERENCES templates (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_template_fields_template ON template_fields (template_id);
