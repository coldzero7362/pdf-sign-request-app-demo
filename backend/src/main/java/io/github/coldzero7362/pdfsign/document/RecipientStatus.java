package io.github.coldzero7362.pdfsign.document;

/**
 * 서명자: WAITING(순차에서 차례 대기, 메일 안 감) → REQUESTED → VIEWED → SIGNED / REJECTED.
 * 참조자·요청자: WAITING → NOTIFIED (완료 메일 받음).
 */
public enum RecipientStatus {
    WAITING, REQUESTED, VIEWED, SIGNED, REJECTED, NOTIFIED;

    /** 이미 요청 메일을 받은 서명자인지 (반려·회수 알림 대상, 지침 5·6) */
    public boolean wasRequested() {
        return this == REQUESTED || this == VIEWED || this == SIGNED;
    }
}
