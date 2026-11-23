package io.github.coldzero7362.pdfsign.document;

import io.github.coldzero7362.pdfsign.auth.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService service;
    private final CurrentUser currentUser;

    public DocumentController(DocumentService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    public record PageView(int pageNo, double width, double height, int rotation) {
    }

    public record DocumentView(UUID id, String fileName, long sizeBytes, String sha256, int pageCount,
                               List<PageView> pages, Instant createdAt) {

        public static DocumentView of(Document d) {
            return new DocumentView(d.getPublicId(), d.getFileName(), d.getSizeBytes(), d.getSha256(),
                d.getPageCount(),
                d.getPages().stream()
                    .map(p -> new PageView(p.getPageNo(), p.getWidth(), p.getHeight(), p.getRotation()))
                    .toList(),
                d.getCreatedAt());
        }
    }

    @Operation(summary = "PDF 업로드", description = "20MB, 50쪽 이하. 암호 걸린 PDF는 거부한다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentView upload(Authentication auth, @RequestParam("file") MultipartFile file) {
        return DocumentView.of(service.upload(currentUser.require(auth).getId(), file));
    }

    @Operation(summary = "문서 정보")
    @GetMapping("/{id}")
    public DocumentView get(Authentication auth, @PathVariable UUID id) {
        return DocumentView.of(service.get(currentUser.require(auth).getId(), id));
    }

    @Operation(summary = "원본 PDF 내려받기")
    @GetMapping("/{id}/file")
    public ResponseEntity<InputStreamResource> file(Authentication auth, @PathVariable UUID id) {
        Document doc = service.get(currentUser.require(auth).getId(), id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(doc.getSizeBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(doc.getFileName(), java.nio.charset.StandardCharsets.UTF_8)
                    .build().toString())
            // PDF를 브라우저에서 직접 열어도 스크립트가 이 사이트 권한으로 돌지 않게 한다
            .header("Content-Security-Policy", "sandbox")
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .body(new InputStreamResource(service.open(doc)));
    }
}
