package io.github.coldzero7362.pdfsign.document;

import io.github.coldzero7362.pdfsign.audit.AuditService;
import io.github.coldzero7362.pdfsign.common.Tokens;
import io.github.coldzero7362.pdfsign.mail.MailOutbox;
import io.github.coldzero7362.pdfsign.mail.MailTemplates;
import io.github.coldzero7362.pdfsign.pdf.PdfStore;
import io.github.coldzero7362.pdfsign.sign.PdfFlattener;
import io.github.coldzero7362.pdfsign.storage.FileStorage;
import io.github.coldzero7362.pdfsign.template.SigningMode;
import io.github.coldzero7362.pdfsign.user.AppUser;
import io.github.coldzero7362.pdfsign.user.AppUserRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문서 진행 규칙. 발송, 순차 진행, 완료, 반려·회수 알림을 한곳에서 처리한다.
 * 모든 메서드는 호출한 쪽 트랜잭션 안에서 돈다 (메일은 커밋 뒤에 나간다).
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class DocumentFlow {

    private final RecipientRepository recipients;
    private final DocumentFieldRepository fields;
    private final AppUserRepository users;
    private final PdfStore pdfs;
    private final FileStorage storage;
    private final PdfFlattener flattener;
    private final AuditService audit;
    private final MailOutbox mail;
    private final MailTemplates templates;
    private final Clock clock;

    public DocumentFlow(RecipientRepository recipients, DocumentFieldRepository fields, AppUserRepository users,
                        PdfStore pdfs, FileStorage storage, PdfFlattener flattener, AuditService audit,
                        MailOutbox mail, MailTemplates templates, Clock clock) {
        this.recipients = recipients;
        this.fields = fields;
        this.users = users;
        this.pdfs = pdfs;
        this.storage = storage;
        this.flattener = flattener;
        this.audit = audit;
        this.mail = mail;
        this.templates = templates;
        this.clock = clock;
    }

    /** 발송 직후: 비순차면 모든 서명자에게, 순차면 첫 서명자에게만 요청 (지침 9). */
    public void start(Document d, List<Recipient> signers) {
        if (d.getSigningMode() == SigningMode.PARALLEL) {
            signers.forEach(s -> request(d, s));
        } else {
            signers.stream().min(Comparator.comparingInt(Recipient::getOrderNo)).ifPresent(s -> request(d, s));
        }
    }

    /** 서명자 한 명이 끝났을 때: 순차면 다음 사람에게, 모두 끝났으면 완료 처리. */
    public void afterSigned(Document d) {
        List<Recipient> all = recipients.findByDocumentIdOrderByRoleAscOrderNoAsc(d.getId());
        List<Recipient> signers = all.stream().filter(Recipient::isSigner).toList();
        if (signers.stream().allMatch(s -> s.getStatus() == RecipientStatus.SIGNED)) {
            finish(d, all);
            return;
        }
        if (d.getSigningMode() == SigningMode.SEQUENTIAL) {
            Optional<Recipient> next = signers.stream()
                .filter(s -> s.getStatus() == RecipientStatus.WAITING)
                .min(Comparator.comparingInt(Recipient::getOrderNo));
            next.ifPresent(s -> request(d, s));
        }
    }

    /** 반려 (지침 5): 즉시 종료하고 요청자와, 이미 요청을 받은 다른 서명자에게 알린다. */
    public void reject(Document d, Recipient who, String reason) {
        Instant now = clock.instant();
        who.reject(reason, now);
        d.reject(now);
        audit.record(d.getId(), who.getId(), who.getEmail(), "REJECTED", reason);
        AppUser owner = owner(d);
        mail.send(templates.rejected(owner.getEmail(), d.getTitle(), who.getName(), reason,
            templates.dashboardLink(d.getPublicId().toString())));
        for (Recipient r : recipients.findByDocumentIdOrderByRoleAscOrderNoAsc(d.getId())) {
            if (r.isSigner() && !r.getId().equals(who.getId()) && r.getStatus().wasRequested()) {
                mail.send(templates.rejected(r.getEmail(), d.getTitle(), who.getName(), reason, null));
            }
        }
    }

    /** 회수 (지침 6): 이미 요청을 받은 서명자에게 알린다. */
    public void recall(Document d, AppUser by) {
        d.recall(clock.instant());
        audit.record(d.getId(), null, by.getEmail(), "RECALLED", null);
        for (Recipient r : recipients.findByDocumentIdOrderByRoleAscOrderNoAsc(d.getId())) {
            if (r.isSigner() && r.getStatus().wasRequested() && r.getStatus() != RecipientStatus.SIGNED) {
                mail.send(templates.recalled(r.getEmail(), r.getName(), d.getTitle()));
            }
        }
    }

    /** 요청 메일 다시 보내기 (웹 전용, 지침 19). 링크를 새로 발급한다. */
    public void remind(Document d, Recipient r, AppUser by) {
        String raw = Tokens.random();
        r.rotateAccessToken(Tokens.sha256(raw));
        AppUser owner = owner(d);
        mail.send(templates.invitation(r.getEmail(), r.getName(), owner.getName(), owner.getEmail(), d.getTitle(),
            d.getMessage(), d.getExpiresAt(), templates.signLink(raw)));
        audit.record(d.getId(), r.getId(), by.getEmail(), "REMINDED", r.getEmail());
    }

    // ---------- 내부 ----------

    private void request(Document d, Recipient s) {
        String raw = Tokens.random();
        s.request(Tokens.sha256(raw), clock.instant());
        AppUser owner = owner(d);
        mail.send(templates.invitation(s.getEmail(), s.getName(), owner.getName(), owner.getEmail(), d.getTitle(),
            d.getMessage(), d.getExpiresAt(), templates.signLink(raw)));
        audit.record(d.getId(), s.getId(), "system", "REQUESTED", s.getSlotLabel() + " · " + s.getEmail());
    }

    /** 모든 서명이 끝남: PDF 합성, 완료 링크를 서명자·참조자·요청자에게 (지침 12). */
    private void finish(Document d, List<Recipient> all) {
        Instant now = clock.instant();
        audit.record(d.getId(), null, "system", "COMPLETED", null);
        byte[] result = flattener.flatten(new PdfFlattener.Input(d, pdfs.read(d.getPdfStorageKey()), all,
            fields.findByDocumentIdOrderByPageNoAscYAscXAsc(d.getId()), pdfs::read, audit.list(d.getId())));
        String key;
        try {
            key = storage.save(result, "pdf");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        d.complete(key, Tokens.sha256Hex(result), result.length, now);

        AppUser owner = owner(d);
        List<Recipient> targets = new java.util.ArrayList<>(all.stream()
            .filter(r -> r.getRole() != RecipientRole.OWNER).toList());
        targets.add(recipients.save(Recipient.owner(d.getId(), owner.getEmail(), owner.getName())));
        for (Recipient r : targets) {
            String raw = Tokens.random();
            r.issueResultLink(Tokens.sha256(raw));
            mail.send(templates.completedToRecipient(r.getEmail(), r.getName(), d.getTitle(), templates.signLink(raw)));
        }
        audit.record(d.getId(), null, "system", "RESULT_SENT", "완료 링크 " + targets.size() + "명");
    }

    private AppUser owner(Document d) {
        return users.findById(d.getOwnerId()).orElseThrow();
    }
}
