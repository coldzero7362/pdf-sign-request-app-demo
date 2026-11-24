package io.github.coldzero7362.pdfsign.document;

/** 문서 상태. IN_PROGRESS 말고는 모두 종료 상태다 (지침 용어 "종료처리"). */
public enum DocumentStatus {
    IN_PROGRESS, COMPLETED, REJECTED, RECALLED, EXPIRED;

    public boolean isOpen() {
        return this == IN_PROGRESS;
    }
}
