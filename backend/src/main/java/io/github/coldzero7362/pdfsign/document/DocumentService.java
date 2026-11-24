package io.github.coldzero7362.pdfsign.document;

import io.github.coldzero7362.pdfsign.audit.AuditService;
import io.github.coldzero7362.pdfsign.document.DocumentDtos.AuditView;
import io.github.coldzero7362.pdfsign.document.DocumentDtos.CreateRequest;
import io.github.coldzero7362.pdfsign.document.DocumentDtos.DocumentDetail;
import io.github.coldzero7362.pdfsign.document.DocumentDtos.DocumentSummary;
import io.github.coldzero7362.pdfsign.document.DocumentDtos.FieldView;
import io.github.coldzero7362.pdfsign.document.DocumentDtos.LinkedDocument;
import io.github.coldzero7362.pdfsign.document.DocumentDtos.PageView;
import io.github.coldzero7362.pdfsign.document.DocumentDtos.RecipientView;
import io.github.coldzero7362.pdfsign.document.DocumentDtos.SignerInput;
import io.github.coldzero7362.pdfsign.document.DocumentDtos.ViewerInput;
import io.github.coldzero7362.pdfsign.template.Template;
import io.github.coldzero7362.pdfsign.template.TemplateField;
import io.github.coldzero7362.pdfsign.template.TemplateFieldType;
import io.github.coldzero7362.pdfsign.template.TemplateService;
import io.github.coldzero7362.pdfsign.template.TemplateSlot;
import io.github.coldzero7362.pdfsign.user.AppUser;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 문서: 서명 요청(API), 목록·상세, 회수, 메일 다시 보내기. */
@Service
public class DocumentService {

    static final Duration REMIND_COOLDOWN = Duration.ofMinutes(1);
    static final Duration REMIND_WINDOW = Duration.ofHours(24);
    static final int REMIND_MAX = 5;

    private final DocumentRepository documents;
    private final RecipientRepository recipients;
    private final DocumentFieldRepository fields;
    private final TemplateService templates;
    private final DocumentFlow flow;
    private final DocumentExpiry expiry;
    private final AuditService audit;
    private final Clock clock;

    public DocumentService(DocumentRepository documents, RecipientRepository recipients,
                           DocumentFieldRepository fields, TemplateService templates, DocumentFlow flow,
                           DocumentExpiry expiry, AuditService audit, Clock clock) {
        this.documents = documents;
        this.recipients = recipients;
        this.fields = fields;
        this.templates = templates;
        this.flow = flow;
        this.expiry = expiry;
        this.audit = audit;
        this.clock = clock;
    }

    // ---------- 서명 요청 (지침 4, 11) ----------

    /**
     * 템플릿 번호와 수신자 정보로 문서를 만들고 발송한다.
     * 같은 Idempotency-Key로 다시 부르면 새로 만들지 않고 처음 만든 문서를 돌려준다.
     */
    @Transactional
    public DocumentDetail create(AppUser owner, CreateRequest req, String idempotencyKey) {
        if (idempotencyKey != null) {
            if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
                throw bad("Idempotency-Key는 1~100자여야 합니다.");
            }
            var existing = documents.findByOwnerIdAndIdempotencyKey(owner.getId(), idempotencyKey);
            if (existing.isPresent()) {
                return detail(existing.get());
            }
        }
        Template t = templates.findForRequest(owner.getId(), req.templateNo().strip());
        return send(owner, t, req, null, idempotencyKey);
    }

    /** 문서 만들기 + 발송 (재발송도 이 길을 쓴다, 지침 13). */
    @Transactional
    public DocumentDetail send(AppUser owner, Template t, CreateRequest req, Document previous, String idempotencyKey) {
        List<String> problems = templates.problemsOf(t);
        if (!problems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "템플릿 " + t.getTemplateNo() + "을(를) 아직 요청에 쓸 수 없습니다: " + String.join(" ", problems));
        }
        List<TemplateSlot> slots = templates.slotsOf(t);
        Map<String, SignerInput> bySlot = new HashMap<>();
        Set<String> emails = new HashSet<>();
        for (SignerInput s : req.signers()) {
            if (bySlot.put(s.slot(), s) != null) {
                throw bad("같은 자리가 두 번 들어 있습니다: " + s.slot());
            }
            if (!emails.add(s.email().strip().toLowerCase(Locale.ROOT))) {
                throw bad("같은 이메일의 서명자가 두 번 들어 있습니다: " + s.email());
            }
        }
        Set<String> slotKeys = slots.stream().map(TemplateSlot::getSlotKey).collect(Collectors.toSet());
        for (String k : bySlot.keySet()) {
            if (!slotKeys.contains(k)) {
                throw bad("템플릿에 없는 자리입니다: " + k + " (가능한 자리: " + String.join(", ", slotKeys) + ")");
            }
        }
        for (TemplateSlot s : slots) {
            if (!bySlot.containsKey(s.getSlotKey())) {
                throw bad("자리 " + s.getSlotKey() + "(" + s.getLabel() + ")의 서명자가 없습니다.");
            }
        }
        int period = req.periodDays() == null ? t.getDefaultPeriodDays() : req.periodDays();
        String title = req.title() == null || req.title().isBlank() ? t.getName() : req.title().strip();
        Instant now = clock.instant();

        Document d = documents.save(Document.fromTemplate(t, title, blankToNull(req.message()), period,
            previous == null ? null : previous.getId(), idempotencyKey, now));

        Map<String, Recipient> signerBySlot = new HashMap<>();
        List<Recipient> signers = new ArrayList<>();
        for (TemplateSlot s : slots) {
            SignerInput in = bySlot.get(s.getSlotKey());
            Recipient r = recipients.save(Recipient.signer(d.getId(), s.getSlotKey(), s.getLabel(), s.getOrderNo(),
                in.email().strip(), in.name().strip()));
            signerBySlot.put(s.getSlotKey(), r);
            signers.add(r);
        }
        int vo = 1;
        for (ViewerInput v : req.viewers() == null ? List.<ViewerInput>of() : req.viewers()) {
            recipients.save(Recipient.viewer(d.getId(), vo++, v.email().strip(), v.name().strip()));
        }
        for (TemplateField f : templates.fieldsOf(t)) {
            Recipient r = f.getType() == TemplateFieldType.TEXT_FIXED ? null : signerBySlot.get(f.getSlotKey());
            fields.save(new DocumentField(d.getId(), r == null ? null : r.getId(), f.getSlotKey(), f.getType(),
                f.getPageNo(), f.getX(), f.getY(), f.getWidth(), f.getHeight(), f.isRequired(), f.getLabel(),
                f.getFixedValue()));
        }
        audit.record(d.getId(), null, previous == null ? null : previous.getId(), owner.getEmail(), "SENT",
            "템플릿 " + t.getTemplateNo() + " (" + t.getRevision() + "판) · " + d.getSigningMode() + " · 서명자 "
                + signers.size() + "명, 참조자 " + (vo - 1) + "명");
        flow.start(d, signers);
        return detail(d);
    }

    // ---------- 조회 ----------

    public record ListQuery(DocumentStatus status, String q, Instant from, Instant to, boolean hidden,
                            int page, int size) {
    }

    @Transactional
    public List<DocumentSummary> list(AppUser owner, ListQuery query) {
        expiry.sweepOwner(owner.getId());
        Specification<Document> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("ownerId"), owner.getId()));
            ps.add(query.hidden() ? cb.isNotNull(root.get("hiddenAt")) : cb.isNull(root.get("hiddenAt")));
            if (query.status() != null) {
                ps.add(cb.equal(root.get("status"), query.status()));
            }
            if (query.from() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("sentAt"), query.from()));
            }
            if (query.to() != null) {
                ps.add(cb.lessThan(root.get("sentAt"), query.to()));
            }
            if (query.q() != null && !query.q().isBlank()) {
                String like = "%" + query.q().strip().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                Subquery<Long> sub = cq.subquery(Long.class);
                var r = sub.from(Recipient.class);
                sub.select(r.get("documentId")).where(cb.like(cb.lower(r.get("email")), like, '\\'));
                ps.add(cb.or(cb.like(cb.lower(root.get("title")), like, '\\'), root.get("id").in(sub)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        int size = Math.min(Math.max(query.size(), 1), 100);
        List<Document> list = documents.findAll(spec,
            PageRequest.of(Math.max(query.page(), 0), size, Sort.by(Sort.Direction.DESC, "sentAt"))).getContent();
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Recipient>> by = recipients.findByDocumentIdIn(list.stream().map(Document::getId).toList())
            .stream().collect(Collectors.groupingBy(Recipient::getDocumentId));
        return list.stream().map(d -> {
            List<Recipient> signers = by.getOrDefault(d.getId(), List.of()).stream().filter(Recipient::isSigner)
                .sorted(Comparator.comparingInt(Recipient::getOrderNo)).toList();
            int signed = (int) signers.stream().filter(s -> s.getStatus() == RecipientStatus.SIGNED).count();
            String current = d.getStatus().isOpen()
                ? signers.stream().filter(Recipient::canSign).map(Recipient::getName).collect(Collectors.joining(", "))
                : null;
            return new DocumentSummary(d.getPublicId(), d.getTitle(), d.getStatus(), d.getTemplateNo(),
                d.getSigningMode(), signers.size(), signed, current, d.getSentAt(), d.getExpiresAt(),
                d.getCompletedAt(), d.getHiddenAt() != null);
        }).toList();
    }

    @Transactional
    public DocumentDetail detail(AppUser owner, UUID id) {
        Document d = load(owner, id);
        expiry.expireIfDue(d, clock.instant());
        return detail(d);
    }

    // ---------- 회수 · 메일 다시 보내기 ----------

    @Transactional
    public DocumentDetail recall(AppUser owner, UUID id) {
        Document d = load(owner, id);
        expiry.expireIfDue(d, clock.instant());
        flow.recall(d, owner);
        return detail(d);
    }

    /** 진행 중인 서명자에게 요청 메일을 다시 보낸다. 1분 간격, 24시간에 5번까지. */
    @Transactional
    public DocumentDetail remind(AppUser owner, UUID id, UUID recipientId) {
        Document d = load(owner, id);
        Instant now = clock.instant();
        expiry.expireIfDue(d, now);
        if (!d.getStatus().isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "진행 중인 문서에서만 다시 보낼 수 있습니다.");
        }
        Recipient r = recipients.findByDocumentIdOrderByRoleAscOrderNoAsc(d.getId()).stream()
            .filter(x -> x.getPublicId().equals(recipientId)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!r.canSign()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "지금 서명할 차례인 서명자에게만 다시 보낼 수 있습니다.");
        }
        var recent = audit.recentFor(r.getId(), "REMINDED", now.minus(REMIND_WINDOW));
        if (!recent.isEmpty() && now.isBefore(recent.getFirst().getCreatedAt().plus(REMIND_COOLDOWN))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "방금 보냈습니다. 1분 뒤에 다시 보낼 수 있습니다.");
        }
        if (recent.size() >= REMIND_MAX) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "이 서명자에게는 24시간 동안 " + REMIND_MAX + "번까지 다시 보낼 수 있습니다.");
        }
        flow.remind(d, r, owner);
        return detail(d);
    }

    @Transactional(readOnly = true)
    public Document completed(AppUser owner, UUID id) {
        Document d = load(owner, id);
        if (d.getResultStorageKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "아직 완료되지 않았습니다.");
        }
        return d;
    }

    // ---------- 내부 ----------

    Document load(AppUser owner, UUID id) {
        return documents.findByPublicIdAndOwnerId(id, owner.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    DocumentDetail detail(Document d) {
        List<Recipient> rs = recipients.findByDocumentIdOrderByRoleAscOrderNoAsc(d.getId());
        Map<Long, String> slotOf = rs.stream().filter(r -> r.getSlotKey() != null)
            .collect(Collectors.toMap(Recipient::getId, Recipient::getSlotKey));
        List<RecipientView> signers = rs.stream().filter(Recipient::isSigner)
            .sorted(Comparator.comparingInt(Recipient::getOrderNo)).map(DocumentService::view).toList();
        List<RecipientView> viewers = rs.stream().filter(r -> r.getRole() == RecipientRole.VIEWER)
            .map(DocumentService::view).toList();
        List<FieldView> fv = fields.findByDocumentIdOrderByPageNoAscYAscXAsc(d.getId()).stream()
            .map(f -> new FieldView(f.getPublicId(), f.getSlotKey(), f.getType(), f.getPageNo(), f.getX(), f.getY(),
                f.getWidth(), f.getHeight(), f.isRequired(), f.getLabel(), f.getFixedValue(), f.getTextValue(),
                f.getFileKey() != null, f.getFilledAt()))
            .toList();

        // 재발송으로 이어진 문서 (앞 문서 + 이 문서로 다시 보낸 문서)
        List<LinkedDocument> linked = new ArrayList<>();
        Map<Long, UUID> idMap = new HashMap<>();
        if (d.getPreviousDocumentId() != null) {
            documents.findById(d.getPreviousDocumentId()).ifPresent(p -> {
                linked.add(new LinkedDocument(p.getPublicId(), p.getTitle(), p.getStatus(), "PREVIOUS"));
                idMap.put(p.getId(), p.getPublicId());
            });
        }
        for (Document n : documents.findByPreviousDocumentId(d.getId())) {
            linked.add(new LinkedDocument(n.getPublicId(), n.getTitle(), n.getStatus(), "NEXT"));
            idMap.put(n.getId(), n.getPublicId());
        }
        List<AuditView> av = audit.list(d.getId()).stream()
            .map(e -> new AuditView(e.getCreatedAt(), e.getEventType(), e.getActor(), e.getDetail(), e.getIp(),
                e.getRelatedDocumentId() == null ? null : idMap.get(e.getRelatedDocumentId())))
            .toList();
        boolean canRecall = d.getStatus().isOpen() && !d.isPastExpiry(clock.instant());
        return new DocumentDetail(d.getPublicId(), d.getTitle(), d.getMessage(), d.getStatus(), d.getTemplateNo(),
            d.getTemplateRevision(), d.getSigningMode(), d.getPeriodDays(), d.getSentAt(), d.getExpiresAt(),
            d.getCompletedAt(), d.getRejectedAt(), d.getRecalledAt(), d.getExpiredAt(), d.getHiddenAt(),
            d.getPdfFileName(), d.getPageCount(),
            d.getPages().stream().map(p -> new PageView(p.getPageNo(), p.getWidth(), p.getHeight(), p.getRotation()))
                .toList(),
            d.getResultSha256(), d.getResultSizeBytes(), signers, viewers, fv, av, linked, canRecall);
    }

    private static RecipientView view(Recipient r) {
        return new RecipientView(r.getPublicId(), r.getRole().name(), r.getSlotKey(), r.getSlotLabel(),
            r.getOrderNo(), r.getEmail(), r.getName(), r.getStatus(), r.getRequestedAt(), r.getViewedAt(),
            r.getSignedAt(), r.getRejectedAt(), r.getRejectReason());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }
}
