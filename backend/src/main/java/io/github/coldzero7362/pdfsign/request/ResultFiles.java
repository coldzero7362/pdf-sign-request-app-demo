package io.github.coldzero7362.pdfsign.request;

import io.github.coldzero7362.pdfsign.storage.FileStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** 완료본 PDF 응답. 요청자와 수신자가 같이 쓴다. */
@Component
public class ResultFiles {

    private final FileStorage storage;

    public ResultFiles(FileStorage storage) {
        this.storage = storage;
    }

    public ResponseEntity<InputStreamResource> download(SignRequest r) {
        try {
            String name = r.getTitle().replaceAll("[\\p{Cntrl}\"\\\\/:*?<>|]", "_") + " (완료).pdf";
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(r.getResultSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .header("Content-Security-Policy", "sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new InputStreamResource(storage.open(r.getResultStorageKey())));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
