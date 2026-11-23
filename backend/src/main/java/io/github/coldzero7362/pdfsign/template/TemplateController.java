package io.github.coldzero7362.pdfsign.template;

import io.github.coldzero7362.pdfsign.auth.CurrentUser;
import io.github.coldzero7362.pdfsign.template.TemplateDtos.SaveRequest;
import io.github.coldzero7362.pdfsign.template.TemplateDtos.TemplateSummary;
import io.github.coldzero7362.pdfsign.template.TemplateDtos.TemplateView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 템플릿 API. 조회는 API 토큰(templates:read)으로도 되고,
 * 만들기·수정·삭제는 웹(로그인 세션)에서만 된다 (SecurityConfig).
 */
@Tag(name = "템플릿")
@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {

    private final TemplateService service;
    private final CurrentUser currentUser;

    public TemplateController(TemplateService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @Operation(summary = "내 템플릿 목록")
    @GetMapping
    public List<TemplateSummary> list(Authentication auth) {
        return service.list(currentUser.require(auth));
    }

    @Operation(summary = "템플릿 상세", description = "수신자 자리, 칸, 설정과 이 템플릿으로 요청할 때의 JSON 예시(requestExample)")
    @GetMapping("/{templateNo}")
    public TemplateView get(Authentication auth, @PathVariable String templateNo) {
        return service.get(currentUser.require(auth), templateNo);
    }

    @Operation(summary = "템플릿 PDF")
    @GetMapping("/{templateNo}/pdf")
    public ResponseEntity<InputStreamResource> pdf(Authentication auth, @PathVariable String templateNo) {
        Template t = service.loadForPdf(currentUser.require(auth), templateNo);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(t.getPdfSizeBytes())
            .header("Content-Security-Policy", "sandbox")
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .body(new InputStreamResource(service.openPdf(t)));
    }

    @Operation(summary = "템플릿 만들기 (웹 전용)", description = "PDF를 올리면 T-0001 형식 번호가 붙는다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateView create(Authentication auth, @RequestParam("file") MultipartFile file,
                               @RequestParam(value = "name", required = false) String name) {
        return service.create(currentUser.require(auth), file, name);
    }

    @Operation(summary = "템플릿 저장 (웹 전용)", description = "이름, 순차/비순차, 기본 기간, 수신자 자리, 칸을 통째로 저장한다.")
    @PutMapping("/{templateNo}")
    public TemplateView save(Authentication auth, @PathVariable String templateNo,
                             @Valid @RequestBody SaveRequest req) {
        return service.save(currentUser.require(auth), templateNo, req);
    }

    @Operation(summary = "템플릿 삭제 (웹 전용)", description = "목록에서 숨긴다. 이미 보낸 문서에는 영향이 없다.")
    @DeleteMapping("/{templateNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable String templateNo) {
        service.delete(currentUser.require(auth), templateNo);
    }
}
