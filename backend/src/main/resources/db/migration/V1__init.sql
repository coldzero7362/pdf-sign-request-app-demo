-- 골격 단계: 스키마 연결 확인용. 실제 테이블은 기능 단계에서 V2부터 추가한다.
CREATE TABLE app_info (
    info_key   VARCHAR(50)  NOT NULL PRIMARY KEY,
    info_value VARCHAR(255) NOT NULL,
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO app_info (info_key, info_value) VALUES ('schema', 'docsign');
