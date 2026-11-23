package io.github.coldzero7362.pdfsign.template;

import io.github.coldzero7362.pdfsign.document.DocumentService;
import io.github.coldzero7362.pdfsign.document.DocumentService.StoredPdf;
import io.github.coldzero7362.pdfsign.template.TemplateDtos.FieldInput;
import io.github.coldzero7362.pdfsign.template.TemplateDtos.FieldView;
import io.github.coldzero7362.pdfsign.template.TemplateDtos.PageView;
import io.github.coldzero7362.pdfsign.template.TemplateDtos.PdfView;
import io.github.coldzero7362.pdfsign.template.TemplateDtos.SaveRequest;
import io.github.coldzero7362.pdfsign.template.TemplateDtos.SlotInput;
import io.github.coldzero7362.pdfsign.template.TemplateDtos.SlotView;
import io.github.coldzero7362.pdfsign.template.TemplateDtos.TemplateSummary;
import io.github.coldzero7362.pdfsign.template.TemplateDtos.TemplateView;
import io.github.coldzero7362.pdfsign.user.AppUser;
import io.github.coldzero7362.pdfsign.user.AppUserRepository;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 템플릿 만들기·저장·조회·삭제 (지침 1, 9~11, 14). */
@Service
public class TemplateService {

    static final double MIN_FIELD_SIZE = 8;

    private final TemplateRepository templates;
    private final TemplateSlotRepository slots;
    private final TemplateFieldRepository fields;
    private final AppUserRepository users;
    private final DocumentService pdfs;
    private final Clock clock;

    public TemplateService(TemplateRepository templates, TemplateSlotRepository slots,
                           TemplateFieldRepository fields, AppUserRepository users, DocumentService pdfs,
                           Clock clock) {
        this.templates = templates;
        this.slots = slots;
        this.fields = fields;
        this.users = users;
        this.pdfs = pdfs;
        this.clock = clock;
    }

    /** PDF를 올려 새 템플릿을 만든다. 서명자 1 자리 하나로 시작한다. */
    @Transactional
    public TemplateView create(AppUser owner, MultipartFile pdf, String name) {
        StoredPdf stored = pdfs.store(pdf);
        // 사용자 행을 잠가 순번이 겹치지 않게 한다
        AppUser locked = users.lockById(owner.getId()).orElseThrow();
        String templateNo = TemplateNumbers.format(locked.nextTemplateSeq());
        String finalName = name == null || name.isBlank()
            ? stored.fileName().replaceAll("(?i)\\.pdf$", "")
            : name.strip();
        if (finalName.length() > 100) {
            finalName = finalName.substring(0, 100);
        }
        List<TemplatePage> pages = stored.pages().stream()
            .map(p -> new TemplatePage(p.getPageNo(), p.getWidth(), p.getHeight(), p.getRotation()))
            .toList();
        Template t = templates.save(new Template(owner.getId(), templateNo, finalName, stored.storageKey(),
            stored.fileName(), stored.sizeBytes(), stored.sha256(), pages, clock.instant()));
        slots.save(new TemplateSlot(t.getId(), "signer1", "서명자 1", 1));
        return view(t);
    }

    @Transactional(readOnly = true)
    public List<TemplateSummary> list(AppUser owner) {
        List<Template> list = templates.findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(owner.getId());
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(Template::getId).toList();
        Map<Long, List<TemplateSlot>> slotsBy = slots.findByTemplateIdIn(ids).stream()
            .collect(Collectors.groupingBy(TemplateSlot::getTemplateId));
        Map<Long, List<TemplateField>> fieldsBy = fields.findByTemplateIdIn(ids).stream()
            .collect(Collectors.groupingBy(TemplateField::getTemplateId));
        return list.stream().map(t -> {
            List<TemplateSlot> ss = slotsBy.getOrDefault(t.getId(), List.of());
            List<TemplateField> fs = fieldsBy.getOrDefault(t.getId(), List.of());
            return new TemplateSummary(t.getTemplateNo(), t.getName(), t.getSigningMode(), t.getDefaultPeriodDays(),
                ss.size(), fs.size(), problems(ss, fs).isEmpty(), t.getRevision(), t.getCreatedAt(), t.getUpdatedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public TemplateView get(AppUser owner, String templateNo) {
        return view(load(owner, templateNo));
    }

    /** 템플릿 내용(설정, 자리, 칸)을 통째로 저장한다. 저장할 때마다 revision이 오른다. */
    @Transactional
    public TemplateView save(AppUser owner, String templateNo, SaveRequest req) {
        Template t = load(owner, templateNo);

        Set<String> keys = new HashSet<>();
        for (SlotInput s : req.slots()) {
            if (!keys.add(s.key())) {
                throw bad("자리 키가 중복되었습니다: " + s.key());
            }
        }
        for (FieldInput f : req.fields()) {
            validateField(t, f, keys);
        }

        fields.deleteByTemplateId(t.getId());
        slots.deleteByTemplateId(t.getId());
        int order = 1;
        for (SlotInput s : req.slots()) {
            slots.save(new TemplateSlot(t.getId(), s.key(), s.label().strip(), order++));
        }
        for (FieldInput f : req.fields()) {
            boolean fixed = f.type() == TemplateFieldType.TEXT_FIXED;
            fields.save(new TemplateField(t.getId(), fixed ? null : f.slotKey(), f.type(), f.page(),
                f.x(), f.y(), f.width(), f.height(), !fixed && f.isRequired(), blankToNull(f.label()),
                fixed ? f.fixedValue().strip() : null));
        }
        Template fresh = templates.findById(t.getId()).orElseThrow();
        fresh.updateSettings(req.name().strip(), req.signingMode(), req.defaultPeriodDays(), clock.instant());
        return view(fresh);
    }

    /** 삭제는 숨김 처리. 이미 보낸 문서는 발송 때 복사해 둔 내용을 쓰므로 영향이 없다. */
    @Transactional
    public void delete(AppUser owner, String templateNo) {
        load(owner, templateNo).delete(clock.instant());
    }

    @Transactional(readOnly = true)
    public Template loadForPdf(AppUser owner, String templateNo) {
        return load(owner, templateNo);
    }

    public InputStream openPdf(Template t) {
        return pdfs.openKey(t.getPdfStorageKey());
    }

    // ---------- 내부 ----------

    Template load(AppUser owner, String templateNo) {
        return templates.findByOwnerIdAndTemplateNoAndDeletedAtIsNull(owner.getId(), templateNo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "템플릿을 찾을 수 없습니다: " + templateNo));
    }

    private static void validateField(Template t, FieldInput f, Set<String> slotKeys) {
        if (f.type() == TemplateFieldType.TEXT_FIXED) {
            if (f.fixedValue() == null || f.fixedValue().isBlank()) {
                throw bad("고정 텍스트 칸에는 값을 넣어 주세요.");
            }
        } else {
            if (f.slotKey() == null || !slotKeys.contains(f.slotKey())) {
                throw bad("칸을 채울 수신자 자리가 없습니다: " + f.slotKey());
            }
            if (f.fixedValue() != null && !f.fixedValue().isBlank()) {
                throw bad("고정 값은 고정 텍스트 칸에만 넣을 수 있습니다.");
            }
        }
        TemplatePage page = t.page(f.page()).orElseThrow(() -> bad("없는 페이지입니다: " + f.page()));
        boolean finite = Double.isFinite(f.x()) && Double.isFinite(f.y())
            && Double.isFinite(f.width()) && Double.isFinite(f.height());
        if (!finite || f.width() < MIN_FIELD_SIZE || f.height() < MIN_FIELD_SIZE) {
            throw bad("칸 크기는 " + (int) MIN_FIELD_SIZE + "pt 이상이어야 합니다.");
        }
        double eps = 0.01;
        if (f.x() < -eps || f.y() < -eps || f.x() + f.width() > page.getWidth() + eps
            || f.y() + f.height() > page.getHeight() + eps) {
            throw bad(f.page() + "쪽 칸이 페이지 밖으로 나갔습니다.");
        }
    }

    /** 요청에 쓸 수 있는 상태인지. 비어 있으면 준비 완료. */
    static List<String> problems(List<TemplateSlot> ss, List<TemplateField> fs) {
        List<String> out = new ArrayList<>();
        if (ss.isEmpty()) {
            out.add("수신자 자리가 하나 이상 있어야 합니다.");
        }
        Set<String> withFields = fs.stream().filter(f -> f.getType().filledBySigner())
            .map(TemplateField::getSlotKey).collect(Collectors.toSet());
        for (TemplateSlot s : ss) {
            if (!withFields.contains(s.getSlotKey())) {
                out.add(s.getLabel() + "에게 입력 칸이 없습니다.");
            }
        }
        return out;
    }

    private TemplateView view(Template t) {
        List<TemplateSlot> ss = slots.findByTemplateIdOrderByOrderNo(t.getId());
        List<TemplateField> fs = fields.findByTemplateIdOrderByPageNoAscYAscXAsc(t.getId());
        Map<String, Long> counts = fs.stream().filter(f -> f.getSlotKey() != null)
            .collect(Collectors.groupingBy(TemplateField::getSlotKey, Collectors.counting()));
        List<String> problems = problems(ss, fs);
        return new TemplateView(t.getTemplateNo(), t.getName(), t.getSigningMode(), t.getDefaultPeriodDays(),
            t.getRevision(), problems.isEmpty(), problems,
            new PdfView(t.getPdfFileName(), t.getPdfSizeBytes(), t.getPdfSha256(), t.getPageCount(),
                t.getPages().stream().map(p -> new PageView(p.getPageNo(), p.getWidth(), p.getHeight(), p.getRotation()))
                    .toList()),
            ss.stream().map(s -> new SlotView(s.getSlotKey(), s.getLabel(), s.getOrderNo(),
                counts.getOrDefault(s.getSlotKey(), 0L).intValue())).toList(),
            fs.stream().map(f -> new FieldView(f.getPublicId(), f.getSlotKey(), f.getType(), f.getPageNo(), f.getX(),
                f.getY(), f.getWidth(), f.getHeight(), f.isRequired(), f.getLabel(), f.getFixedValue())).toList(),
            requestExample(t, ss), t.getCreatedAt(), t.getUpdatedAt());
    }

    /** 이 템플릿으로 서명을 요청할 때 보낼 JSON 예시 (API 사용자 안내용). */
    private static Map<String, Object> requestExample(Template t, List<TemplateSlot> ss) {
        Map<String, Object> ex = new LinkedHashMap<>();
        ex.put("templateNo", t.getTemplateNo());
        ex.put("title", t.getName());
        ex.put("message", "확인 후 서명 부탁드립니다.");
        ex.put("periodDays", t.getDefaultPeriodDays());
        List<Map<String, String>> signers = new ArrayList<>();
        int i = 1;
        for (TemplateSlot s : ss) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("slot", s.getSlotKey());
            m.put("email", "signer" + i + "@example.com");
            m.put("name", s.getLabel());
            signers.add(m);
            i++;
        }
        ex.put("signers", signers);
        ex.put("viewers", List.of(Map.of("email", "viewer@example.com", "name", "참조자")));
        return ex;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
