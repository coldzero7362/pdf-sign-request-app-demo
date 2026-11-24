package io.github.coldzero7362.pdfsign.pdf;

/** 페이지 크기 (PDF 포인트, 회전 반영 후 화면에 보이는 방향). */
public record PdfPageInfo(int pageNo, double width, double height, int rotation) {
}
