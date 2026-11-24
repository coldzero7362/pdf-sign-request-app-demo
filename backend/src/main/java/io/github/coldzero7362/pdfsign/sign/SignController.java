package io.github.coldzero7362.pdfsign.sign;

import io.github.coldzero7362.pdfsign.document.ResultFiles;
import io.github.coldzero7362.pdfsign.sign.SignDtos.RejectRequest;
import io.github.coldzero7362.pdfsign.sign.SignDtos.FillRequest;
import io.github.coldzero7362.pdfsign.sign.SignDtos.LinkInfo;
import io.github.coldzero7362.pdfsign.sign.SignDtos.SignField;
import io.github.coldzero7362.pdfsign.sign.SignDtos.OtpSent;
import io.github.coldzero7362.pdfsign.sign.SignDtos.SigningView;
import io.github.coldzero7362.pdfsign.sign.SignDtos.TokenRequest;
import io.github.coldzero7362.pdfsign.sign.SignDtos.VerifyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * 수신자용 API (계정 없음). 메일 링크 토큰 → 이메일 OTP → 세션 순서로 인증한다.
 * 쿠키 세션을 쓰므로 변경 요청은 CSRF 헤더(X-XSRF-TOKEN)가 필요하다.
 */
@Tag(name = "수신자")
@RestController
@RequestMapping("/sign/api")
public class SignController {

    private final SignAccessService access;
    private final SigningService signing;
    private final ResultFiles resultFiles;

    public SignController(SignAccessService access, SigningService signing, ResultFiles resultFiles) {
        this.access = access;
        this.signing = signing;
        this.resultFiles = resultFiles;
    }

    @Operation(summary = "CSRF 쿠키 받기", description = "첫 POST 전에 한 번 호출해 XSRF-TOKEN 쿠키를 받는다.")
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf() {
        // CsrfFilter가 응답에 XSRF-TOKEN 쿠키를 붙인다
    }

    @Operation(summary = "링크 정보 (인증 전)")
    @PostMapping("/session/link")
    public LinkInfo link(@Valid @RequestBody TokenRequest req, HttpServletRequest http) {
        return access.linkInfo(req.token(), http.getSession(false));
    }

    @Operation(summary = "인증번호 메일 보내기")
    @PostMapping("/session/otp")
    public OtpSent otp(@Valid @RequestBody TokenRequest req) {
        return access.sendOtp(req.token());
    }

    @Operation(summary = "인증번호 확인")
    @PostMapping("/session/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody VerifyRequest req, HttpServletRequest http) {
        access.verify(req.token(), req.code(), http);
    }

    @Operation(summary = "작성 마치고 나가기")
    @PostMapping("/session/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void end(HttpServletRequest http) {
        access.end(http.getSession(false));
    }

    @Operation(summary = "작성 화면 정보 (문서, 내 칸)")
    @GetMapping("/request")
    public SigningView view(HttpServletRequest http) {
        return signing.view(http.getSession(false));
    }

    @Operation(summary = "원본 PDF")
    @GetMapping("/document")
    public ResponseEntity<InputStreamResource> document(HttpServletRequest http) {
        return resultFiles.original(signing.document(http.getSession(false)));
    }

    @Operation(summary = "텍스트·날짜·체크 칸 채우기")
    @PutMapping("/fields/{id}")
    public SignField fill(@PathVariable UUID id, @Valid @RequestBody FillRequest req, HttpServletRequest http) {
        return signing.fillText(http.getSession(false), id, req.value());
    }

    @Operation(summary = "서명 칸에 이미지 올리기 (PNG/JPEG, 2MB 이하)")
    @PostMapping(path = "/fields/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SignField image(@PathVariable UUID id, @RequestParam("file") MultipartFile file, HttpServletRequest http)
            throws IOException {
        if (file.getSize() > ImageSanitizer.MAX_BYTES) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
                "이미지는 2MB 이하만 올릴 수 있습니다.");
        }
        return signing.fillImage(http.getSession(false), id, file.getBytes());
    }

    @Operation(summary = "내가 올린 서명 이미지")
    @GetMapping("/fields/{id}/image")
    public ResponseEntity<byte[]> imageOf(@PathVariable UUID id, HttpServletRequest http) {
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .body(signing.image(http.getSession(false), id));
    }

    @Operation(summary = "제출")
    @PostMapping("/submit")
    public SigningView submit(HttpServletRequest http) {
        return signing.submit(http.getSession(false));
    }

    @Operation(summary = "반려", description = "후순위 서명자와 관계없이 문서가 즉시 종료된다 (지침 5).")
    @PostMapping("/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@Valid @RequestBody RejectRequest req, HttpServletRequest http) {
        signing.reject(http.getSession(false), req.reason());
    }

    @Operation(summary = "완료본 PDF 내려받기")
    @GetMapping("/result")
    public ResponseEntity<InputStreamResource> result(HttpServletRequest http) {
        return resultFiles.download(signing.result(http.getSession(false)));
    }
}
