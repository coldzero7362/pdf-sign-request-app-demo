package io.github.coldzero7362.pdfsign.document;

import io.github.coldzero7362.pdfsign.auth.CurrentUser;
import io.github.coldzero7362.pdfsign.document.DocumentDtos.CreateRequest;
import io.github.coldzero7362.pdfsign.document.DocumentDtos.DocumentDetail;
import io.github.coldzero7362.pdfsign.document.DocumentDtos.DocumentSummary;
import io.github.coldzero7362.pdfsign.document.DocumentService.ListQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "문서")
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService service;
    private final CurrentUser currentUser;
    private final ResultFiles files;

    public DocumentController(DocumentService service, CurrentUser currentUser, ResultFiles files) {
        this.service = service;
        this.currentUser = currentUser;
        this.files = files;
    }

    @Operation(summary = "서명 요청",
        description = "템플릿 번호와 자리별 서명자, 참조자를 보내면 문서를 만들고 발송한다. "
            + "Idempotency-Key 헤더를 주면 같은 키로 다시 보내도 한 번만 만든다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDetail create(Authentication auth, @Valid @RequestBody CreateRequest req,
                                 @Parameter(description = "중복 요청 방지 키 (선택, 1~100자)")
                                 @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.create(currentUser.require(auth), req, key);
    }

    @Operation(summary = "보낸 문서 목록", description = "q: 제목·수신자 이메일, from/to: 발송 시각(ISO-8601), hidden: 숨긴 문서만")
    @GetMapping
    public List<DocumentSummary> list(Authentication auth,
                                      @RequestParam(required = false) DocumentStatus status,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(required = false) Instant from,
                                      @RequestParam(required = false) Instant to,
                                      @RequestParam(defaultValue = "false") boolean hidden,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return service.list(currentUser.require(auth), new ListQuery(status, q, from, to, hidden, page, size));
    }

    @Operation(summary = "문서 상세", description = "서명자별 상태, 칸, 감사 추적, 재발송으로 이어진 문서")
    @GetMapping("/{id}")
    public DocumentDetail detail(Authentication auth, @PathVariable UUID id) {
        return service.detail(currentUser.require(auth), id);
    }

    @Operation(summary = "회수", description = "진행 중이고 서명 요청 기간 안일 때만 (지침 6)")
    @PostMapping("/{id}/recall")
    public DocumentDetail recall(Authentication auth, @PathVariable UUID id) {
        return service.recall(currentUser.require(auth), id);
    }

    @Operation(summary = "요청 메일 다시 보내기 (웹 전용)", description = "링크를 새로 발급한다. 1분 간격, 24시간에 5번까지.")
    @PostMapping("/{id}/recipients/{recipientId}/remind")
    public DocumentDetail remind(Authentication auth, @PathVariable UUID id, @PathVariable UUID recipientId) {
        return service.remind(currentUser.require(auth), id, recipientId);
    }

    @Operation(summary = "원본 PDF")
    @GetMapping("/{id}/pdf")
    public ResponseEntity<InputStreamResource> pdf(Authentication auth, @PathVariable UUID id) {
        return files.original(service.load(currentUser.require(auth), id));
    }

    @Operation(summary = "완료 PDF")
    @GetMapping("/{id}/result")
    public ResponseEntity<InputStreamResource> result(Authentication auth, @PathVariable UUID id) {
        return files.download(service.completed(currentUser.require(auth), id));
    }
}
