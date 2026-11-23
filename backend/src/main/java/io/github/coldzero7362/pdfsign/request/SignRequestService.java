package io.github.coldzero7362.pdfsign.request;

import io.github.coldzero7362.pdfsign.audit.AuditService;
import io.github.coldzero7362.pdfsign.common.Tokens;
import io.github.coldzero7362.pdfsign.mail.MailOutbox;
import io.github.coldzero7362.pdfsign.mail.MailTemplates;
import io.github.coldzero7362.pdfsign.document.Document;
import io.github.coldzero7362.pdfsign.document.DocumentController.DocumentView;
import io.github.coldzero7362.pdfsign.document.DocumentPage;
import io.github.coldzero7362.pdfsign.document.DocumentRepository;
import io.github.coldzero7362.pdfsign.document.DocumentService;
import io.github.coldzero7362.pdfsign.request.RequestDtos.AuditView;
import io.github.coldzero7362.pdfsign.request.RequestDtos.CreateRequest;
import io.github.coldzero7362.pdfsign.request.RequestDtos.FieldInput;
import io.github.coldzero7362.pdfsign.request.RequestDtos.FieldView;
import io.github.coldzero7362.pdfsign.request.RequestDtos.LayoutRequest;
import io.github.coldzero7362.pdfsign.request.RequestDtos.RecipientInput;
import io.github.coldzero7362.pdfsign.request.RequestDtos.RecipientView;
import io.github.coldzero7362.pdfsign.request.RequestDtos.RequestDetail;
import io.github.coldzero7362.pdfsign.request.RequestDtos.RequestSummary;
import io.github.coldzero7362.pdfsign.request.RequestDtos.UpdateRequest;
import io.github.coldzero7362.pdfsign.user.AppUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SignRequestService {

    static final double MIN_FIELD_SIZE = 8;
    static final Duration MAX_EXPIRY = Duration.ofDays(90);

    private final SignRequestRepository requests;
    private final RecipientRepository recipients;
    private final SignFieldRepository fields;
    private final DocumentRepository documents;
    private final DocumentService documentService;
    private final AuditService audit;
    private final MailOutbox mail;
    private final MailTemplates templates;
    private final Clock clock;

    public SignRequestService(SignRequestRepository requests, RecipientRepository recipients,
                              SignFieldRepository fields, DocumentRepository documents,
                              DocumentService documentService, AuditService audit,
                              MailOutbox mail, MailTemplates templates, Clock clock) {
        this.requests = requests;
        this.recipients = recipients;
        this.fields = fields;
        this.documents = documents;
        this.documentService = documentService;
        this.audit = audit;
        this.mail = mail;
        this.templates = templates;
        this.clock = clock;
    }

    // ---------- 조회 ----------

    @Transactional(readOnly = true)
    public List<RequestSummary> list(AppUser owner, RequestStatus status) {
        List<SignRequest> list = status == null
            ? requests.findTop100ByOwnerIdOrderByCreatedAtDesc(owner.getId())
            : requests.findTop100ByOwnerIdAndStatusOrderByCreatedAtDesc(owner.getId(), status);
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Recipient>> byRequest = recipients
            .findByRequestIdIn(list.stream().map(SignRequest::getId).toList()).stream()
            .collect(Collectors.groupingBy(Recipient::getRequestId));
        Map<Long, String> docNames = documents
            .findAllById(list.stream().map(SignRequest::getDocumentId).collect(Collectors.toSet())).stream()
            .collect(Collectors.toMap(Document::getId, Document::getFileName));

        return list.stream().map(r -> {
            List<Recipient> signers = byRequest.getOrDefault(r.getId(), List.of()).stream()
                .filter(x -> x.getRole() == RecipientRole.SIGNER).toList();
            int done = (int) signers.stream().filter(x -> x.getStatus() == RecipientStatus.COMPLETED).count();
            return new RequestSummary(r.getPublicId(), r.getTitle(), r.getStatus(),
                docNames.get(r.getDocumentId()), signers.size(), done,
                r.getCreatedAt(), r.getSentAt(), r.getExpiresAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public RequestDetail detail(AppUser owner, UUID id) {
        return toDetail(load(owner, id));
    }

    @Transactional(readOnly = true)
    public List<AuditView> auditTrail(AppUser owner, UUID id) {
        SignRequest r = load(owner, id);
        return audit.list(r.getId()).stream()
            .map(e -> new AuditView(e.getCreatedAt(), e.getEventType(), e.getActor(), e.getDetail(), e.getIp()))
            .toList();
    }

    // ---------- 초안 작성 ----------

    @Transactional
    public RequestDetail create(AppUser owner, CreateRequest req) {
        Document doc = documentService.get(owner.getId(), req.documentId());
        Instant now = clock.instant();
        validateExpiry(req.expiresAt(), now);
        SignRequest r = requests.save(new SignRequest(owner.getId(), doc.getId(),
            req.title().strip(), blankToNull(req.message()), req.expiresAt(), now));
        audit.record(r.getId(), null, owner.getEmail(), "CREATED", r.getTitle());
        return toDetail(r);
    }

    @Transactional
    public RequestDetail update(AppUser owner, UUID id, UpdateRequest req) {
        SignRequest r = load(owner, id);
        Instant now = clock.instant();
        validateExpiry(req.expiresAt(), now);
        r.update(req.title().strip(), blankToNull(req.message()), req.expiresAt(), now);
        return toDetail(r);
    }

    /** 초안의 수신자와 칸을 통째로 바꾼다. 저장할 때마다 수신자·칸 ID는 새로 발급된다. */
    @Transactional
    public RequestDetail saveLayout(AppUser owner, UUID id, LayoutRequest req) {
        SignRequest r = load(owner, id);
        r.requireDraft();
        Document doc = documents.findById(r.getDocumentId()).orElseThrow();

        // 수신자 검증: key와 이메일 중복 금지
        Set<String> keys = new HashSet<>();
        Set<String> emails = new HashSet<>();
        for (RecipientInput in : req.recipients()) {
            if (!keys.add(in.key())) {
                throw bad("수신자 key가 중복되었습니다: " + in.key());
            }
            if (!emails.add(in.email().strip().toLowerCase(Locale.ROOT))) {
                throw bad("같은 이메일의 수신자가 두 번 들어 있습니다: " + in.email());
            }
        }
        Map<String, RecipientInput> recipientByKey = req.recipients().stream()
            .collect(Collectors.toMap(RecipientInput::key, Function.identity()));
        for (FieldInput f : req.fields()) {
            RecipientInput owner0 = recipientByKey.get(f.recipientKey());
            if (owner0 == null) {
                throw bad("없는 수신자 key를 가리키는 칸이 있습니다: " + f.recipientKey());
            }
            if (owner0.role() != RecipientRole.SIGNER) {
                throw bad("열람자(VIEWER)에게는 입력 칸을 줄 수 없습니다: " + owner0.email());
            }
            validateBox(doc, f);
        }

        // 칸 → 수신자 순으로 지우고 다시 넣는다 (외래 키 순서)
        fields.deleteByRequestId(r.getId());
        recipients.deleteByRequestId(r.getId());

        Map<String, Recipient> saved = new HashMap<>();
        int order = 1;
        for (RecipientInput in : req.recipients()) {
            saved.put(in.key(), recipients.save(new Recipient(r.getId(), in.email().strip(),
                in.name().strip(), in.role(), order++)));
        }
        for (FieldInput f : req.fields()) {
            fields.save(new SignField(r.getId(), saved.get(f.recipientKey()).getId(), f.type(), f.page(),
                f.x(), f.y(), f.width(), f.height(), f.required(), blankToNull(f.label())));
        }
        r.touch(clock.instant());
        return toDetail(requests.findById(r.getId()).orElseThrow());
    }

    // ---------- 상태 전환 ----------

    /**
     * 발송. 수신자마다 메일 링크 토큰을 만들고(해시만 저장), 서명자에게 초대 메일을 보낸다.
     * 열람자는 완료되었을 때 완료본 링크를 받는다 (완료 시 모든 수신자의 토큰을 새로 발급해 보낸다).
     */
    @Transactional
    public RequestDetail send(AppUser owner, UUID id) {
        SignRequest r = load(owner, id);
        r.requireDraft();
        Instant now = clock.instant();
        if (r.getExpiresAt() != null && !r.getExpiresAt().isAfter(now)) {
            throw bad("만료일이 이미 지났습니다. 만료일을 바꾼 뒤 발송해 주세요.");
        }
        List<Recipient> list = recipients.findByRequestIdOrderBySortOrder(r.getId());
        List<Recipient> signers = list.stream().filter(x -> x.getRole() == RecipientRole.SIGNER).toList();
        if (signers.isEmpty()) {
            throw bad("서명자(SIGNER)가 한 명 이상 있어야 합니다.");
        }
        Set<Long> withFields = fields.findByRequestIdOrderByPageNoAscYAscXAsc(r.getId()).stream()
            .map(SignField::getRecipientId).collect(Collectors.toSet());
        for (Recipient s : signers) {
            if (!withFields.contains(s.getId())) {
                throw bad("입력 칸이 없는 서명자가 있습니다: " + s.getEmail());
            }
        }
        r.markSent(now);
        for (Recipient x : list) {
            String raw = Tokens.random();
            x.markSent(Tokens.sha256(raw));
            if (x.isSigner()) {
                mail.send(templates.invitation(x.getEmail(), x.getName(), owner.getName(), owner.getEmail(),
                    r.getTitle(), r.getMessage(), r.getExpiresAt(), templates.signLink(raw)));
            }
        }
        audit.record(r.getId(), null, owner.getEmail(), "SENT",
            "수신자 " + list.size() + "명: " + list.stream().map(Recipient::getEmail).collect(Collectors.joining(", ")));
        return toDetail(r);
    }

    @Transactional
    public RequestDetail cancel(AppUser owner, UUID id) {
        SignRequest r = load(owner, id);
        r.cancel(clock.instant());
        audit.record(r.getId(), null, owner.getEmail(), "CANCELED", null);
        for (Recipient x : recipients.findByRequestIdOrderBySortOrder(r.getId())) {
            if (x.isSigner() && !x.isDone()) {
                mail.send(templates.canceledToRecipient(x.getEmail(), x.getName(), r.getTitle()));
            }
        }
        return toDetail(r);
    }

    /** 초안만 지울 수 있다. 발송한 요청은 감사 기록을 남기기 위해 지우지 않는다. */
    @Transactional
    public void deleteDraft(AppUser owner, UUID id) {
        SignRequest r = load(owner, id);
        r.requireDraft();
        fields.deleteByRequestId(r.getId());
        recipients.deleteByRequestId(r.getId());
        audit.deleteForDraft(r.getId());
        requests.delete(requests.findById(r.getId()).orElseThrow());
        requests.flush();
    }

    /** 완료본 PDF (요청자용). */
    @Transactional(readOnly = true)
    public SignRequest completed(AppUser owner, UUID id) {
        SignRequest r = load(owner, id);
        if (r.getResultStorageKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "아직 완료되지 않았습니다.");
        }
        return r;
    }

    // ---------- 내부 ----------

    private SignRequest load(AppUser owner, UUID id) {
        return requests.findByPublicIdAndOwnerId(id, owner.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void validateExpiry(Instant expiresAt, Instant now) {
        if (expiresAt == null) {
            return;
        }
        if (!expiresAt.isAfter(now)) {
            throw bad("만료일은 지금 이후여야 합니다.");
        }
        if (expiresAt.isAfter(now.plus(MAX_EXPIRY))) {
            throw bad("만료일은 최대 90일 뒤까지 정할 수 있습니다.");
        }
    }

    private static void validateBox(Document doc, FieldInput f) {
        DocumentPage page = doc.page(f.page())
            .orElseThrow(() -> bad("없는 페이지입니다: " + f.page()));
        boolean finite = Double.isFinite(f.x()) && Double.isFinite(f.y())
            && Double.isFinite(f.width()) && Double.isFinite(f.height());
        if (!finite || f.width() < MIN_FIELD_SIZE || f.height() < MIN_FIELD_SIZE) {
            throw bad("칸 크기는 " + (int) MIN_FIELD_SIZE + "pt 이상이어야 합니다.");
        }
        double eps = 0.01;
        if (f.x() < -eps || f.y() < -eps
            || f.x() + f.width() > page.getWidth() + eps
            || f.y() + f.height() > page.getHeight() + eps) {
            throw bad(f.page() + "쪽 칸이 페이지 밖으로 나갔습니다.");
        }
    }

    private RequestDetail toDetail(SignRequest r) {
        Document doc = documents.findById(r.getDocumentId()).orElseThrow();
        List<Recipient> rs = recipients.findByRequestIdOrderBySortOrder(r.getId());
        Map<Long, UUID> recipientIds = rs.stream()
            .collect(Collectors.toMap(Recipient::getId, Recipient::getPublicId));
        List<RecipientView> rv = rs.stream()
            .map(x -> new RecipientView(x.getPublicId(), x.getEmail(), x.getName(), x.getRole(), x.getStatus(),
                x.getSortOrder(), x.getCompletedAt(), x.getDeclinedAt(), x.getDeclineReason()))
            .toList();
        List<FieldView> fv = fields.findByRequestIdOrderByPageNoAscYAscXAsc(r.getId()).stream()
            .map(f -> new FieldView(f.getPublicId(), recipientIds.get(f.getRecipientId()), f.getType(),
                f.getPageNo(), f.getX(), f.getY(), f.getWidth(), f.getHeight(), f.isRequired(), f.getLabel(),
                f.getTextValue(), f.getFileKey() != null, f.getFilledAt()))
            .toList();
        return new RequestDetail(r.getPublicId(), r.getTitle(), r.getMessage(), r.getStatus(), r.getExpiresAt(),
            r.getCreatedAt(), r.getUpdatedAt(), r.getSentAt(), r.getCompletedAt(), r.getCanceledAt(),
            r.getDeclinedAt(), r.getResultSha256(), r.getResultSizeBytes(),
            DocumentView.of(doc), rv, fv);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
