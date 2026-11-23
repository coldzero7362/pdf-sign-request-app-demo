package io.github.coldzero7362.pdfsign.template;

/**
 * 템플릿 번호 형식. 정식 형식이 정해질 때까지 요청자별 순번 "T-0001"로 임시 운영한다 (지침 11).
 * 형식을 바꿀 때는 이 클래스만 고친다.
 */
public final class TemplateNumbers {

    private TemplateNumbers() {
    }

    public static String format(int seq) {
        return String.format("T-%04d", seq);
    }
}
