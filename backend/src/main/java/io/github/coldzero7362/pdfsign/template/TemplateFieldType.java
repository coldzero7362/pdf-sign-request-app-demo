package io.github.coldzero7362.pdfsign.template;

/**
 * 템플릿 칸 종류 (지침 10, 14).
 * 워터마크와 첨부파일은 별도 단계에서 추가한다 (REDESIGN-PLAN 7, 8단계).
 */
public enum TemplateFieldType {
    SIGNATURE,
    DATE,
    /** 서명자가 입력하는 텍스트 박스 */
    TEXT,
    /** 요청자가 템플릿에서 값을 채워 두는 텍스트 박스. 서명자는 고칠 수 없다 */
    TEXT_FIXED,
    CHECKBOX;

    public boolean filledBySigner() {
        return this != TEXT_FIXED;
    }
}
