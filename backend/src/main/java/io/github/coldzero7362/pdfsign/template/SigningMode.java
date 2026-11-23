package io.github.coldzero7362.pdfsign.template;

/** 서명 순서 (지침 9). */
public enum SigningMode {
    /** 순서대로. 앞 서명자가 끝나야 다음 서명자에게 요청이 간다. */
    SEQUENTIAL,
    /** 순서 없이. 발송할 때 모든 서명자에게 한 번에 보낸다. */
    PARALLEL
}
