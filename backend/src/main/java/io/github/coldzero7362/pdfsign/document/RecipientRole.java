package io.github.coldzero7362.pdfsign.document;

/**
 * SIGNER: 서명자 (템플릿 자리). VIEWER: 참조자 (완료 문서만 받음).
 * OWNER: 요청자. 완료 문서 링크(인증번호 확인)를 받기 위해 완료 시점에 만든다 (지침 12).
 */
public enum RecipientRole {
    SIGNER, VIEWER, OWNER
}
