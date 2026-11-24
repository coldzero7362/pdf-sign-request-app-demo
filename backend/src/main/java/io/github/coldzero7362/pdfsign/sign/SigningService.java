package io.github.coldzero7362.pdfsign.sign;

import io.github.coldzero7362.pdfsign.audit.AuditService;
import io.github.coldzero7362.pdfsign.document.Document;
import io.github.coldzero7362.pdfsign.document.DocumentDtos.PageView;
import io.github.coldzero7362.pdfsign.document.DocumentField;
import io.github.coldzero7362.pdfsign.document.DocumentFieldRepository;
import io.github.coldzero7362.pdfsign.document.DocumentFlow;
import io.github.coldzero7362.pdfsign.document.DocumentStatus;
import io.github.coldzero7362.pdfsign.document.Recipient;
import io.github.coldzero7362.pdfsign.sign.SignAccessService.Access;
import io.github.coldzero7362.pdfsign.sign.SignDtos.SignField;
import io.github.coldzero7362.pdfsign.sign.SignDtos.SigningView;
import io.github.coldzero7362.pdfsign.storage.FileStorage;
import io.github.coldzero7362.pdfsign.template.TemplateFieldType;
import io.github.coldzero7362.pdfsign.user.AppUser;
import io.github.coldzero7362.pdfsign.user.AppUserRepository;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 수신자 서명 화면의 동작: 보기, 칸 채우기, 제출, 반려, 완료본. */
@Service
public class SigningService {

    static final int MAX_TEXT = 500;

    private final SignAccessService access;
    private final DocumentFieldRepository fields;
    private final AppUserRepository users;
    private final FileStorage storage;
    private final ImageSanitizer images;
    private final DocumentFlow flow;
    private final AuditService audit;
    private final Clock clock;

    public SigningService(SignAccessService access, DocumentFieldRepository fields, AppUserRepository users,
                          FileStorage storage, ImageSanitizer images, DocumentFlow flow, AuditService audit,
                          Clock clock) {
        this.access = access;
        this.fields = fields;
        this.users = users;
        this.storage = storage;
        this.images = images;
        this.flow = flow;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public SigningView view(HttpSession session) {
        Access a = access.current(session);
        Recipient r = a.recipient();
        Document d = a.document();
        if (r.canSign() && r.getViewedAt() == null) {
            r.markViewed(clock.instant());
            audit.record(d.getId(), r.getId(), r.getEmail(), "VIEWED", null);
        }
        AppUser owner = users.findById(d.getOwnerId()).orElseThrow();
        List<SignField> fs = new ArrayList<>();
        if (r.isSigner() && d.getStatus().isOpen()) {
            for (DocumentField f : fields.findByDocumentIdOrderByPageNoAscYAscXAsc(d.getId())) {
                if (r.getId().equals(f.getRecipientId())) {
                    fs.add(toView(f, true));
                } else if (f.getType() == TemplateFieldType.TEXT_FIXED) {
                    fs.add(toView(f, false));
                }
            }
        }
        return new SigningView(d.getTitle(), d.getMessage(), owner.getName(), owner.getEmail(), d.getStatus(),
            d.getExpiresAt(), r.getName(), r.getEmail(), r.getRole(), r.getStatus(), r.getSlotLabel(), canEdit(a),
            d.getResultStorageKey() != null, d.getPdfFileName(),
            d.getPages().stream().map(p -> new PageView(p.getPageNo(), p.getWidth(), p.getHeight(), p.getRotation()))
                .toList(),
            fs);
    }

    @Transactional(readOnly = true)
    public Document document(HttpSession session) {
        return access.current(session).document();
    }

    @Transactional
    public SignField fillText(HttpSession session, UUID fieldId, String value) {
        Access a = editable(session);
        DocumentField f = myField(a, fieldId);
        String v = value == null ? null : value.strip();
        switch (f.getType()) {
            case TEXT -> {
                if (v != null && v.length() > MAX_TEXT) {
                    throw bad("최대 " + MAX_TEXT + "자까지 입력할 수 있습니다.");
                }
            }
            case DATE -> {
                if (v != null && !v.isEmpty()) {
                    try {
                        LocalDate.parse(v);
                    } catch (DateTimeParseException e) {
                        throw bad("날짜 형식은 YYYY-MM-DD 입니다.");
                    }
                }
            }
            case CHECKBOX -> {
                if (v != null && !v.equals("true") && !v.equals("false")) {
                    throw bad("체크 칸 값은 true 또는 false 입니다.");
                }
            }
            case SIGNATURE -> throw bad("서명 칸에는 이미지를 올려 주세요.");
            case TEXT_FIXED -> throw bad("요청자가 정한 칸은 고칠 수 없습니다.");
        }
        f.fillText(v == null || v.isEmpty() ? null : v, clock.instant());
        return toView(f, true);
    }

    @Transactional
    public SignField fillImage(HttpSession session, UUID fieldId, byte[] upload) {
        Access a = editable(session);
        DocumentField f = myField(a, fieldId);
        if (f.getType() != TemplateFieldType.SIGNATURE) {
            throw bad("이미지는 서명 칸에만 올릴 수 있습니다.");
        }
        byte[] png = images.toPng(upload);
        try {
            f.fillFile(storage.save(png, "png"), clock.instant());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return toView(f, true);
    }

    @Transactional(readOnly = true)
    public byte[] image(HttpSession session, UUID fieldId) {
        Access a = access.current(session);
        DocumentField f = myField(a, fieldId);
        if (f.getFileKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        try {
            return storage.read(f.getFileKey());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 제출. 필수 칸이 모두 채워져야 한다. 순차면 다음 서명자에게, 마지막이면 완료 처리. */
    @Transactional
    public SigningView submit(HttpSession session) {
        Access a = editable(session);
        Recipient r = a.recipient();
        Document d = a.document();
        List<DocumentField> mine = fields.findByRecipientIdOrderByPageNoAscYAscXAsc(r.getId());
        long missing = mine.stream().filter(f -> f.isRequired() && !f.isFilled()).count();
        if (missing > 0) {
            throw bad("필수 칸 " + missing + "개를 채워 주세요.");
        }
        r.sign(clock.instant());
        audit.record(d.getId(), r.getId(), r.getEmail(), "SIGNED", r.getSlotLabel() + " · 칸 " + mine.size() + "개");
        d.touch(clock.instant());
        flow.afterSigned(d);
        return view(session);
    }

    /** 반려 (지침 5): 후순위 서명자와 관계없이 즉시 종료. */
    @Transactional
    public void reject(HttpSession session, String reason) {
        Access a = editable(session);
        String why = reason == null || reason.isBlank() ? null : reason.strip();
        flow.reject(a.document(), a.recipient(), why);
    }

    @Transactional(readOnly = true)
    public Document result(HttpSession session) {
        Access a = access.current(session);
        if (a.document().getStatus() != DocumentStatus.COMPLETED || a.document().getResultStorageKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "아직 완료되지 않았습니다.");
        }
        return a.document();
    }

    // ---------- 내부 ----------

    private Access editable(HttpSession session) {
        Access a = access.current(session);
        if (!canEdit(a)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "더 이상 수정할 수 없습니다.");
        }
        return a;
    }

    private static boolean canEdit(Access a) {
        return a.document().getStatus().isOpen() && a.recipient().canSign();
    }

    private DocumentField myField(Access a, UUID fieldId) {
        return fields.findByPublicIdAndRecipientId(fieldId, a.recipient().getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static SignField toView(DocumentField f, boolean editable) {
        String value = f.getType() == TemplateFieldType.TEXT_FIXED ? f.getFixedValue() : f.getTextValue();
        return new SignField(f.getPublicId(), f.getType(), f.getPageNo(), f.getX(), f.getY(), f.getWidth(),
            f.getHeight(), editable && f.isRequired(), f.getLabel(), value, f.getFileKey() != null, f.isFilled(),
            editable);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
