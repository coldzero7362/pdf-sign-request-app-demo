package io.github.coldzero7362.pdfsign.auth;

import java.util.List;
import java.util.Set;

/** 권한 이름 모음. */
public final class Authorities {

    /** 로그인한 요청자 (세션이든 토큰이든). */
    public static final String USER = "ROLE_USER";
    /** 브라우저 세션으로 인증됨. 토큰 관리처럼 민감한 기능은 세션에서만 허용한다. */
    public static final String SESSION = "AUTH_SESSION";
    /** API 토큰으로 인증됨. */
    public static final String TOKEN = "AUTH_TOKEN";

    /** API 토큰에 줄 수 있는 스코프. */
    public static final String SCOPE_DOCUMENTS_READ = "documents:read";
    /** 서명 요청, 회수, 재발송 */
    public static final String SCOPE_DOCUMENTS_WRITE = "documents:write";
    /** 템플릿 조회 (만들기·수정은 웹 세션 전용) */
    public static final String SCOPE_TEMPLATES_READ = "templates:read";
    public static final Set<String> ALL_SCOPES = Set.of(SCOPE_DOCUMENTS_READ, SCOPE_DOCUMENTS_WRITE, SCOPE_TEMPLATES_READ);

    /** 세션 사용자는 모든 스코프를 가진다. */
    public static final List<String> SESSION_AUTHORITIES = List.of(
        USER, SESSION, "SCOPE_" + SCOPE_DOCUMENTS_READ, "SCOPE_" + SCOPE_DOCUMENTS_WRITE,
        "SCOPE_" + SCOPE_TEMPLATES_READ);

    private Authorities() {
    }
}
