package io.github.coldzero7362.pdfsign.sign;

import io.github.coldzero7362.pdfsign.audit.AuditService;
import io.github.coldzero7362.pdfsign.common.Tokens;
import io.github.coldzero7362.pdfsign.document.Document;
import io.github.coldzero7362.pdfsign.document.DocumentController.PageView;
import io.github.coldzero7362.pdfsign.document.DocumentRepository;
import io.github.coldzero7362.pdfsign.mail.MailOutbox;
import io.github.coldzero7362.pdfsign.mail.MailTemplates;
import io.github.coldzero7362.pdfsign.request.FieldType;
import io.github.coldzero7362.pdfsign.request.Recipient;
import io.github.coldzero7362.pdfsign.request.RecipientRepository;
import io.github.coldzero7362.pdfsign.request.RecipientStatus;
import io.github.coldzero7362.pdfsign.request.RequestStatus;
import io.github.coldzero7362.pdfsign.request.SignField;
import io.github.coldzero7362.pdfsign.request.SignFieldRepository;
import io.github.coldzero7362.pdfsign.request.SignRequest;
import io.github.coldzero7362.pdfsign.sign.SignAccessService.Access;
import io.github.coldzero7362.pdfsign.sign.SignDtos.MyField;
import io.github.coldzero7362.pdfsign.sign.SignDtos.SigningView;
import io.github.coldzero7362.pdfsign.storage.FileStorage;
import io.github.coldzero7362.pdfsign.user.AppUser;
import io.github.coldzero7362.pdfsign.user.AppUserRepository;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 수신자 작성 화면의 동작: 보기, 칸 채우기, 제출, 거절, 완료 처리. */
@Service
public class SigningService {

    static final int MAX_TEXT = 500;

    private final SignAccessService access;
    private final RecipientRepository recipients;
    private final SignFieldRepository fields;
    private final DocumentRepository documents;
    private final AppUserRepository users;
    private final FileStorage storage;
    private final ImageSanitizer images;
    private final PdfFlattener flattener;
    private final AuditService audit;
    private final MailOutbox mail;
    private final MailTemplates templates;
    private final Clock clock;

    public SigningService(SignAccessService access, RecipientRepository recipients, SignFieldRepository fields,
                          DocumentRepository documents, AppUserRepository users, FileStorage storage,
                          ImageSanitizer images, PdfFlattener flattener, AuditService audit, MailOutbox mail,
                          MailTemplates templates, Clock clock) {
        this.access = access;
        this.recipients = recipients;
        this.fields = fields;
        this.documents = documents;
        this.users = users;
        this.storage = storage;
        this.images = images;
        this.flattener = flattener;
        this.audit = audit;
        this.mail = mail;
        this.templates = templates;
        this.clock = clock;
    }

    @Transactional
    public SigningView view(HttpSession session) {
        Access a = access.current(session);
        Recipient r = a.recipient();
        SignRequest req = a.request();
        Instant now = clock.instant();
        if (r.isSigner() && req.getStatus().isOpen() && r.getViewedAt() == null) {
            r.markViewed(now);
            req.markInProgress(now);
            audit.record(req.getId(), r.getId(), r.getEmail(), "VIEWED", null);
        }
        Document doc = documents.findById(req.getDocumentId()).orElseThrow();
        AppUser owner = users.findById(req.getOwnerId()).orElseThrow();
        List<MyField> my = r.isSigner()
            ? fields.findByRecipientIdOrderByPageNoAscYAscXAsc(r.getId()).stream().map(SigningService::toView).toList()
            : List.of();
        return new SigningView(req.getTitle(), req.getMessage(), owner.getName(), owner.getEmail(),
            req.getStatus(), req.getExpiresAt(), r.getName(), r.getEmail(), r.getRole(), r.getStatus(),
            canEdit(a), req.getResultStorageKey() != null, doc.getFileName(),
            doc.getPages().stream()
                .map(p -> new PageView(p.getPageNo(), p.getWidth(), p.getHeight(), p.getRotation())).toList(),
            my);
    }

    @Transactional(readOnly = true)
    public Document document(HttpSession session) {
        Access a = access.current(session);
        return documents.findById(a.request().getDocumentId()).orElseThrow();
    }

    @Transactional
    public MyField fillText(HttpSession session, UUID fieldId, String value) {
        Access a = editable(session);
        SignField f = myField(a, fieldId);
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
        }
        f.fillText(v == null || v.isEmpty() ? null : v, clock.instant());
        return toView(f);
    }

    @Transactional
    public MyField fillImage(HttpSession session, UUID fieldId, byte[] upload) {
        Access a = editable(session);
        SignField f = myField(a, fieldId);
        if (f.getType() != FieldType.SIGNATURE) {
            throw bad("이미지는 서명 칸에만 올릴 수 있습니다.");
        }
        byte[] png = images.toPng(upload);
        try {
            f.fillFile(storage.save(png, "png"), clock.instant());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return toView(f);
    }

    @Transactional(readOnly = true)
    public byte[] image(HttpSession session, UUID fieldId) {
        Access a = access.current(session);
        SignField f = myField(a, fieldId);
        if (f.getFileKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return read(f.getFileKey());
    }

    /** 제출. 필수 칸이 모두 채워져야 한다. 마지막 서명자면 완료본을 만든다. */
    @Transactional
    public SigningView submit(HttpSession session) {
        Access a = editable(session);
        Recipient r = a.recipient();
        SignRequest req = a.request();
        List<SignField> mine = fields.findByRecipientIdOrderByPageNoAscYAscXAsc(r.getId());
        long missing = mine.stream().filter(f -> f.isRequired() && !f.isFilled()).count();
        if (missing > 0) {
            throw bad("필수 칸 " + missing + "개를 채워 주세요.");
        }
        Instant now = clock.instant();
        r.complete(now);
        audit.record(req.getId(), r.getId(), r.getEmail(), "SUBMITTED", "칸 " + mine.size() + "개");

        List<Recipient> all = recipients.findByRequestIdOrderBySortOrder(req.getId());
        boolean allDone = all.stream().filter(Recipient::isSigner)
            .allMatch(x -> x.getStatus() == RecipientStatus.COMPLETED);
        if (allDone) {
            finish(req, all, now);
        }
        return view(session);
    }

    @Transactional
    public void decline(HttpSession session, String reason) {
        Access a = editable(session);
        Recipient r = a.recipient();
        SignRequest req = a.request();
        Instant now = clock.instant();
        String why = reason == null || reason.isBlank() ? null : reason.strip();
        r.decline(why, now);
        req.decline(now);
        audit.record(req.getId(), r.getId(), r.getEmail(), "DECLINED", why);
        AppUser owner = users.findById(req.getOwnerId()).orElseThrow();
        mail.send(templates.declinedToOwner(owner.getEmail(), req.getTitle(), r.getName(), why,
            templates.dashboardLink(req.getPublicId().toString())));
    }

    @Transactional(readOnly = true)
    public SignRequest result(HttpSession session) {
        Access a = access.current(session);
        if (a.request().getStatus() != RequestStatus.COMPLETED || a.request().getResultStorageKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "아직 완료되지 않았습니다.");
        }
        return a.request();
    }

    // ---------- 완료 ----------

    private void finish(SignRequest req, List<Recipient> all, Instant now) {
        Document doc = documents.findById(req.getDocumentId()).orElseThrow();
        audit.record(req.getId(), null, "system", "COMPLETED", null);
        byte[] result = flattener.flatten(new PdfFlattener.Input(req, doc.getFileName(), doc.getSha256(),
            read(doc.getStorageKey()), all, fields.findByRequestIdOrderByPageNoAscYAscXAsc(req.getId()),
            this::read, audit.list(req.getId())));
        String key;
        try {
            key = storage.save(result, "pdf");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        req.complete(key, Tokens.sha256Hex(result), result.length, now);

        // 완료본 링크: 모든 수신자의 토큰을 새로 발급해 보낸다 (이전 링크는 더 이상 쓰지 않음)
        for (Recipient x : all) {
            String raw = Tokens.random();
            x.rotateAccessToken(Tokens.sha256(raw));
            mail.send(templates.completedToRecipient(x.getEmail(), x.getName(), req.getTitle(), templates.signLink(raw)));
        }
        AppUser owner = users.findById(req.getOwnerId()).orElseThrow();
        mail.send(templates.completedToOwner(owner.getEmail(), req.getTitle(),
            templates.dashboardLink(req.getPublicId().toString())));
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
        return a.recipient().isSigner() && a.request().getStatus().isOpen() && !a.recipient().isDone();
    }

    private SignField myField(Access a, UUID fieldId) {
        return fields.findByPublicIdAndRecipientId(fieldId, a.recipient().getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private byte[] read(String key) {
        try {
            return storage.read(key);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static MyField toView(SignField f) {
        return new MyField(f.getPublicId(), f.getType(), f.getPageNo(), f.getX(), f.getY(), f.getWidth(),
            f.getHeight(), f.isRequired(), f.getLabel(), f.getTextValue(), f.getFileKey() != null, f.isFilled());
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
