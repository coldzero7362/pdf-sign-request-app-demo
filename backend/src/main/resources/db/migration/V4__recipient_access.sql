-- 수신자 접근: 메일 링크 토큰(해시), 이메일 OTP(해시), 열람 시각
-- (테스트용 H2와 같이 쓰기 위해 컬럼마다 ALTER 문을 나눈다)
ALTER TABLE recipients ADD COLUMN access_token_hash VARCHAR(64) NULL;
ALTER TABLE recipients ADD COLUMN otp_hash VARCHAR(64) NULL;
ALTER TABLE recipients ADD COLUMN otp_expires_at DATETIME(6) NULL;
ALTER TABLE recipients ADD COLUMN otp_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE recipients ADD COLUMN otp_window_start DATETIME(6) NULL;
ALTER TABLE recipients ADD COLUMN otp_send_count INT NOT NULL DEFAULT 0;
ALTER TABLE recipients ADD COLUMN otp_last_sent_at DATETIME(6) NULL;
ALTER TABLE recipients ADD COLUMN viewed_at DATETIME(6) NULL;

CREATE UNIQUE INDEX uk_recipients_access_token ON recipients (access_token_hash);

-- 완료본 (입력값을 합성하고 감사 기록 페이지를 붙인 PDF)
ALTER TABLE sign_requests ADD COLUMN result_storage_key VARCHAR(100) NULL;
ALTER TABLE sign_requests ADD COLUMN result_sha256 VARCHAR(64) NULL;
ALTER TABLE sign_requests ADD COLUMN result_size_bytes BIGINT NULL;
ALTER TABLE sign_requests ADD COLUMN declined_at DATETIME(6) NULL;
