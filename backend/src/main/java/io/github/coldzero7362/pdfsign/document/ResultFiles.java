package io.github.coldzero7362.pdfsign.document;

import io.github.coldzero7362.pdfsign.pdf.PdfStore;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** 완료 PDF 응답. 요청자(웹·API)와 수신자(인증번호 링크)가 같이 쓴다. */
@Component
public class ResultFiles {

    private final PdfStore pdfs;

    public ResultFiles(PdfStore pdfs) {
        this.pdfs = pdfs;
    }

    public ResponseEntity<InputStreamResource> download(Document d) {
        String name = d.getTitle().replaceAll("[\\p{Cntrl}\"\\\\/:*?<>|]", "_") + " (완료).pdf";
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(d.getResultSizeBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
            .header("Content-Security-Policy", "sandbox")
            .header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .body(new InputStreamResource(pdfs.open(d.getResultStorageKey())));
    }

    /** 원본 PDF (서명 화면·상세 미리보기용). */
    public ResponseEntity<InputStreamResource> original(Document d) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(d.getPdfSizeBytes())
            .header("Content-Security-Policy", "sandbox")
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .body(new InputStreamResource(pdfs.open(d.getPdfStorageKey())));
    }
}
