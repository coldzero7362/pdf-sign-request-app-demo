package io.github.coldzero7362.pdfsign.request;

import io.github.coldzero7362.pdfsign.auth.CurrentUser;
import io.github.coldzero7362.pdfsign.request.RequestDtos.AuditView;
import io.github.coldzero7362.pdfsign.request.RequestDtos.CreateRequest;
import io.github.coldzero7362.pdfsign.request.RequestDtos.LayoutRequest;
import io.github.coldzero7362.pdfsign.request.RequestDtos.RequestDetail;
import io.github.coldzero7362.pdfsign.request.RequestDtos.RequestSummary;
import io.github.coldzero7362.pdfsign.request.RequestDtos.UpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/v1/requests")
public class SignRequestController {

    private final SignRequestService service;
    private final CurrentUser currentUser;
    private final ResultFiles resultFiles;

    public SignRequestController(SignRequestService service, CurrentUser currentUser, ResultFiles resultFiles) {
        this.service = service;
        this.currentUser = currentUser;
        this.resultFiles = resultFiles;
    }

    @Operation(summary = "내 요청 목록 (최근 100건)")
    @GetMapping
    public List<RequestSummary> list(Authentication auth, @RequestParam(required = false) RequestStatus status) {
        return service.list(currentUser.require(auth), status);
    }

    @Operation(summary = "요청 만들기 (초안)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequestDetail create(Authentication auth, @Valid @RequestBody CreateRequest req) {
        return service.create(currentUser.require(auth), req);
    }

    @Operation(summary = "요청 상세 (문서, 수신자, 칸)")
    @GetMapping("/{id}")
    public RequestDetail detail(Authentication auth, @PathVariable UUID id) {
        return service.detail(currentUser.require(auth), id);
    }

    @Operation(summary = "제목·메시지·만료일 수정 (초안만)")
    @PutMapping("/{id}")
    public RequestDetail update(Authentication auth, @PathVariable UUID id, @Valid @RequestBody UpdateRequest req) {
        return service.update(currentUser.require(auth), id, req);
    }

    @Operation(summary = "수신자와 입력 칸 저장 (초안만, 통째로 교체)",
        description = "좌표는 페이지 왼쪽 위가 원점인 PDF 포인트. 회전된 페이지는 화면에 보이는 방향 기준.")
    @PutMapping("/{id}/layout")
    public RequestDetail layout(Authentication auth, @PathVariable UUID id, @Valid @RequestBody LayoutRequest req) {
        return service.saveLayout(currentUser.require(auth), id, req);
    }

    @Operation(summary = "발송")
    @PostMapping("/{id}/send")
    public RequestDetail send(Authentication auth, @PathVariable UUID id) {
        return service.send(currentUser.require(auth), id);
    }

    @Operation(summary = "취소 (진행 중인 요청)")
    @PostMapping("/{id}/cancel")
    public RequestDetail cancel(Authentication auth, @PathVariable UUID id) {
        return service.cancel(currentUser.require(auth), id);
    }

    @Operation(summary = "수신자에게 메일 다시 보내기",
        description = "진행 중이면 초대 메일, 완료 후 30일 이내면 완료본 링크. 링크는 새로 발급되고 이전 링크는 무효. "
            + "수신자마다 1분 간격, 24시간에 5번까지.")
    @PostMapping("/{id}/recipients/{recipientId}/resend")
    public SignRequestService.ResendResult resend(Authentication auth, @PathVariable UUID id,
                                                  @PathVariable UUID recipientId) {
        return service.resend(currentUser.require(auth), id, recipientId);
    }

    @Operation(summary = "초안 삭제")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable UUID id) {
        service.deleteDraft(currentUser.require(auth), id);
    }

    @Operation(summary = "완료본 PDF 내려받기", description = "입력값을 합성하고 감사 기록 페이지를 붙인 PDF")
    @GetMapping("/{id}/result")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.InputStreamResource> result(
            Authentication auth, @PathVariable UUID id) {
        SignRequest r = service.completed(currentUser.require(auth), id);
        return resultFiles.download(r);
    }

    @Operation(summary = "감사 기록")
    @GetMapping("/{id}/audit")
    public List<AuditView> audit(Authentication auth, @PathVariable UUID id) {
        return service.auditTrail(currentUser.require(auth), id);
    }
}
