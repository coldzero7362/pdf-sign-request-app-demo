package io.github.coldzero7362.pdfsign.pdf;

import io.github.coldzero7362.pdfsign.common.Tokens;
import io.github.coldzero7362.pdfsign.storage.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** PDF 검사·저장·읽기. 템플릿과 문서가 같이 쓴다. */
@Component
public class PdfStore {

    public static final long MAX_BYTES = 20L * 1024 * 1024;

    private final PdfInspector inspector;
    private final FileStorage storage;

    public PdfStore(PdfInspector inspector, FileStorage storage) {
        this.inspector = inspector;
        this.storage = storage;
    }

    public record StoredPdf(String storageKey, String fileName, long sizeBytes, String sha256,
                            List<PdfPageInfo> pages) {
    }

    public StoredPdf store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "파일은 20MB 이하만 올릴 수 있습니다.");
        }
        try {
            byte[] content = file.getBytes();
            List<PdfPageInfo> pages = inspector.inspect(content);
            String key = storage.save(content, "pdf");
            return new StoredPdf(key, safeFileName(file.getOriginalFilename()), content.length,
                Tokens.sha256Hex(content), pages);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public InputStream open(String storageKey) {
        try {
            return storage.open(storageKey);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] read(String storageKey) {
        try {
            return storage.read(storageKey);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 경로, 제어 문자, 따옴표를 빼고 255자로 자른다. 확장자는 .pdf로 맞춘다. */
    public static String safeFileName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>|:*?]", "").strip();
        if (name.isEmpty()) {
            name = "document.pdf";
        }
        if (!name.toLowerCase().endsWith(".pdf")) {
            name = name + ".pdf";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
