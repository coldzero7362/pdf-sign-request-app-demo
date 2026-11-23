package io.github.coldzero7362.pdfsign.request;

/**
 * 요청 상태.
 * DRAFT → SENT → IN_PROGRESS → COMPLETED
 *           └→ DECLINED (수신자 거절) / CANCELED (요청자 취소) / EXPIRED (기한 초과)
 */
public enum RequestStatus {
    DRAFT, SENT, IN_PROGRESS, COMPLETED, DECLINED, CANCELED, EXPIRED;

    public boolean isOpen() {
        return this == SENT || this == IN_PROGRESS;
    }
}
