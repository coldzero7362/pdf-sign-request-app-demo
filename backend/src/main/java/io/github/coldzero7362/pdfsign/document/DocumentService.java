package io.github.coldzero7362.pdfsign.document;

import io.github.coldzero7362.pdfsign.storage.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentService {

    public static final long MAX_BYTES = 20L * 1024 * 1024;

    private final DocumentRepository documents;
    private final PdfInspector inspector;
    private final FileStorage storage;
    private final Clock clock;

    public DocumentService(DocumentRepository documents, PdfInspector inspector, FileStorage storage, Clock clock) {
        this.documents = documents;
        this.inspector = inspector;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional
    public Document upload(Long ownerId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "파일은 20MB 이하만 올릴 수 있습니다.");
        }
        try {
            byte[] content = file.getBytes();
            List<DocumentPage> pages = inspector.inspect(content);
            String key = storage.save(content, "pdf");
            return documents.save(new Document(ownerId, safeFileName(file.getOriginalFilename()), key,
                content.length, sha256(content), pages, clock.instant()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional(readOnly = true)
    public Document get(Long ownerId, UUID publicId) {
        return documents.findByPublicIdAndOwnerId(publicId, ownerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public InputStream open(Document document) {
        try {
            return storage.open(document.getStorageKey());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 경로, 제어 문자, 따옴표를 빼고 255자로 자른다. 확장자는 .pdf로 맞춘다. */
    static String safeFileName(String original) {
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

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
