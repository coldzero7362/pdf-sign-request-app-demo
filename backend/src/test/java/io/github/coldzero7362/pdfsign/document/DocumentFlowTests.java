package io.github.coldzero7362.pdfsign.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.coldzero7362.pdfsign.auth.Authorities;
import io.github.coldzero7362.pdfsign.mail.LoggingMailSender;
import io.github.coldzero7362.pdfsign.mail.MailMessage;
import io.github.coldzero7362.pdfsign.mail.MailSender;
import io.github.coldzero7362.pdfsign.user.AppUser;
import io.github.coldzero7362.pdfsign.user.AppUserRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 템플릿 → API 서명 요청 → 서명 → 완료/반려/회수 전체 흐름.
 * 메일은 커밋 뒤에 나가므로 트랜잭션 롤백 없이 돈다 (테스트마다 새 사용자).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DocumentFlowTests {

    static final Pattern LINK = Pattern.compile("/sign/([A-Za-z0-9_-]{43})");
    static final Pattern CODE = Pattern.compile("인증번호: (\\d{6})");

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired @Qualifier("appMailSender") MailSender mailSender;

    LoggingMailSender outbox;
    RequestPostProcessor owner;
    String ownerEmail;
    String bearer;

    @BeforeEach
    void setUp() throws Exception {
        outbox = (LoggingMailSender) mailSender;
        String sub = "doc-" + UUID.randomUUID();
        ownerEmail = sub + "@example.com";
        users.save(new AppUser(sub, ownerEmail, "김요청", Instant.now()));
        owner = oidcLogin().idToken(t -> t.subject(sub))
            .authorities(AuthorityUtils.createAuthorityList(Authorities.SESSION_AUTHORITIES));
        String body = mvc.perform(post("/api/v1/me/tokens").with(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"api\",\"scopes\":[\"documents:read\",\"documents:write\",\"templates:read\"]}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        bearer = "Bearer " + JsonPath.read(body, "$.token");
        outbox.clear();
    }

    // ---------- 준비 ----------

    static byte[] pdf() throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            doc.save(out);
            return out.toByteArray();
        }
    }

    static byte[] png() throws IOException {
        BufferedImage img = new BufferedImage(200, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 200, 60);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** 자리 2개(근로자, 대표), 고정 텍스트 1개짜리 템플릿. */
    String template(String mode) throws Exception {
        String no = JsonPath.read(mvc.perform(multipart("/api/v1/templates")
                .file(new MockMultipartFile("file", "계약서.pdf", "application/pdf", pdf()))
                .param("name", "근로계약서").with(owner).with(csrf()))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.templateNo");
        mvc.perform(put("/api/v1/templates/" + no).with(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"name":"근로계약서","signingMode":"%s","defaultPeriodDays":10,
                     "slots":[{"key":"worker","label":"근로자"},{"key":"ceo","label":"대표"}],
                     "fields":[
                       {"slotKey":"worker","type":"TEXT","page":1,"x":50,"y":100,"width":200,"height":24,"required":true},
                       {"slotKey":"worker","type":"SIGNATURE","page":1,"x":50,"y":700,"width":150,"height":50,"required":true},
                       {"slotKey":"ceo","type":"DATE","page":1,"x":300,"y":100,"width":120,"height":24,"required":true},
                       {"type":"TEXT_FIXED","page":1,"x":50,"y":40,"width":300,"height":24,"fixedValue":"유사인 표준 계약서"}]}
                    """.formatted(mode)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ready").value(true));
        return no;
    }

    String unique(String n) {
        return n + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    ResultActions request(String body) throws Exception {
        return mvc.perform(post("/api/v1/documents").header("Authorization", bearer)
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    String requestBody(String no, String worker, String ceo, String viewer) {
        return """
            {"templateNo":"%s","signers":[{"slot":"worker","email":"%s","name":"홍길동"},
                                          {"slot":"ceo","email":"%s","name":"김대표"}],
             "viewers":[{"email":"%s","name":"인사팀"}]}
            """.formatted(no, worker, ceo, viewer);
    }

    String send(String no, String worker, String ceo, String viewer) throws Exception {
        return JsonPath.read(request(requestBody(no, worker, ceo, viewer))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    List<MailMessage> mailsTo(String email) {
        return outbox.sent().stream().filter(m -> m.to().equals(email)).toList();
    }

    String linkFor(String email) {
        List<MailMessage> mine = mailsTo(email).stream().filter(m -> LINK.matcher(m.text()).find()).toList();
        assertThat(mine).as("mail with link to " + email).isNotEmpty();
        Matcher m = LINK.matcher(mine.getLast().text());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    MockHttpSession login(String token, String email) throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/sign/api/session/otp").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
            .andExpect(status().isOk());
        MailMessage last = mailsTo(email).stream().filter(m -> m.subject().startsWith("[인증번호]")).toList().getLast();
        Matcher m = CODE.matcher(last.text());
        assertThat(m.find()).isTrue();
        mvc.perform(post("/sign/api/session/verify").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"code\":\"" + m.group(1) + "\"}"))
            .andExpect(status().isNoContent());
        return s;
    }

    /** 내 칸을 모두 채우고 제출. 고정 텍스트 칸은 읽기 전용으로 함께 보인다. */
    void signAll(MockHttpSession s) throws Exception {
        String view = mvc.perform(get("/sign/api/request").session(s))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(view, "$.fields[?(@.editable == true)].id");
        List<String> types = JsonPath.read(view, "$.fields[?(@.editable == true)].type");
        for (int i = 0; i < ids.size(); i++) {
            switch (types.get(i)) {
                case "SIGNATURE" -> mvc.perform(multipart("/sign/api/fields/" + ids.get(i) + "/image")
                        .file(new MockMultipartFile("file", "s.png", "image/png", png())).session(s).with(csrf()))
                    .andExpect(status().isOk());
                case "TEXT" -> fill(s, ids.get(i), "홍길동").andExpect(status().isOk());
                case "DATE" -> fill(s, ids.get(i), "2026-11-24").andExpect(status().isOk());
                default -> { }
            }
        }
        mvc.perform(post("/sign/api/submit").session(s).with(csrf())).andExpect(status().isOk());
    }

    ResultActions fill(MockHttpSession s, String id, String v) throws Exception {
        return mvc.perform(put("/sign/api/fields/" + id).session(s).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"" + v + "\"}"));
    }

    // ---------- 테스트 ----------

    @Test
    void parallelFlowCompletesAndSendsResultLinksToEveryone() throws Exception {
        String no = template("PARALLEL");
        String w = unique("w"), c = unique("c"), v = unique("v");
        String id = JsonPath.read(request(requestBody(no, w, c, v))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.title").value("근로계약서"))            // 제목 생략 → 템플릿 이름
            .andExpect(jsonPath("$.periodDays").value(10))                // 기간 생략 → 템플릿 기본값
            .andExpect(jsonPath("$.signers[0].status").value("REQUESTED"))
            .andExpect(jsonPath("$.signers[1].status").value("REQUESTED"))
            .andExpect(jsonPath("$.viewers[0].status").value("WAITING"))
            .andReturn().getResponse().getContentAsString(), "$.id");

        // 비순차: 서명자 둘에게 한 번에, 참조자에게는 아직 안 감
        assertThat(outbox.sent()).extracting(MailMessage::to).containsExactlyInAnyOrder(w, c);

        MockHttpSession sw = login(linkFor(w), w);
        String view = mvc.perform(get("/sign/api/request").session(sw))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slotLabel").value("근로자"))
            .andReturn().getResponse().getContentAsString();
        // 내 칸 2개 + 요청자 고정 텍스트(읽기 전용)
        assertThat((List<?>) JsonPath.read(view, "$.fields[?(@.editable == true)]")).hasSize(2);
        assertThat((List<String>) JsonPath.read(view, "$.fields[?(@.editable == false)].value"))
            .containsExactly("유사인 표준 계약서");
        String fixedId = ((List<String>) JsonPath.read(view, "$.fields[?(@.editable == false)].id")).getFirst();
        fill(sw, fixedId, "바꾸기").andExpect(status().isNotFound());

        signAll(sw);
        String tc = linkFor(c);
        outbox.clear();
        signAll(login(tc, c));

        // 완료 링크: 서명자 둘, 참조자, 요청자 (지침 12)
        assertThat(outbox.sent()).filteredOn(m -> m.subject().startsWith("[완료]"))
            .extracting(MailMessage::to).containsExactlyInAnyOrder(w, c, v, ownerEmail);

        // 요청자도 메일 링크 + 인증번호로 완료본을 받는다
        MockHttpSession so = login(linkFor(ownerEmail), ownerEmail);
        mvc.perform(get("/sign/api/request").session(so))
            .andExpect(jsonPath("$.role").value("OWNER"))
            .andExpect(jsonPath("$.resultAvailable").value(true));
        mvc.perform(get("/sign/api/result").session(so)).andExpect(status().isOk());
        // 참조자도
        mvc.perform(get("/sign/api/result").session(login(linkFor(v), v))).andExpect(status().isOk());

        // 요청자 API: 상세와 완료 PDF
        mvc.perform(get("/api/v1/documents/" + id).header("Authorization", bearer))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.resultSha256").isString())
            .andExpect(jsonPath("$.audit[*].type", org.hamcrest.Matchers.hasItems("SENT", "REQUESTED", "SIGNED",
                "COMPLETED", "RESULT_SENT")));
        byte[] result = mvc.perform(get("/api/v1/documents/" + id + "/result").header("Authorization", bearer))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument out = Loader.loadPDF(result)) {
            String text = new PDFTextStripper().getText(out);
            assertThat(text).contains("유사인 표준 계약서", "홍길동", "2026-11-24", "감사 기록", no, "비순차", "근로자", "대표", "인사팀");
        }
    }

    @Test
    void sequentialSendsToNextSignerOnlyAfterPreviousSigns() throws Exception {
        String no = template("SEQUENTIAL");
        String w = unique("w"), c = unique("c"), v = unique("v");
        String id = send(no, w, c, v);
        assertThat(outbox.sent()).extracting(MailMessage::to).containsExactly(w);
        mvc.perform(get("/api/v1/documents/" + id).header("Authorization", bearer))
            .andExpect(jsonPath("$.signers[0].status").value("REQUESTED"))
            .andExpect(jsonPath("$.signers[1].status").value("WAITING"));

        signAll(login(linkFor(w), w));
        assertThat(mailsTo(c)).hasSize(1);
        mvc.perform(get("/api/v1/documents/" + id).header("Authorization", bearer))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.signers[0].status").value("SIGNED"))
            .andExpect(jsonPath("$.signers[1].status").value("REQUESTED"));

        signAll(login(linkFor(c), c));
        mvc.perform(get("/api/v1/documents/" + id).header("Authorization", bearer))
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void rejectEndsImmediatelyAndNotifiesOnlyThoseAlreadyRequested() throws Exception {
        String no = template("SEQUENTIAL");
        String w = unique("w"), c = unique("c"), v = unique("v");
        String id = send(no, w, c, v);
        MockHttpSession sw = login(linkFor(w), w);
        outbox.clear();
        mvc.perform(post("/sign/api/reject").session(sw).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"급여 조건 다름\"}"))
            .andExpect(status().isNoContent());
        // 요청자에게만. 차례가 안 온 대표, 참조자에게는 메일 없음
        assertThat(outbox.sent()).extracting(MailMessage::to).containsExactly(ownerEmail);
        assertThat(outbox.sent().getFirst().text()).contains("급여 조건 다름");
        mvc.perform(get("/api/v1/documents/" + id).header("Authorization", bearer))
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.signers[0].status").value("REJECTED"))
            .andExpect(jsonPath("$.signers[0].rejectReason").value("급여 조건 다름"))
            .andExpect(jsonPath("$.signers[1].status").value("WAITING"))
            .andExpect(jsonPath("$.canRecall").value(false));
        mvc.perform(get("/sign/api/request").session(sw)).andExpect(status().isGone());
    }

    @Test
    void parallelRejectNotifiesOtherRequestedSigners() throws Exception {
        String no = template("PARALLEL");
        String w = unique("w"), c = unique("c"), v = unique("v");
        send(no, w, c, v);
        MockHttpSession sc = login(linkFor(c), c);
        outbox.clear();
        mvc.perform(post("/sign/api/reject").session(sc).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isNoContent());
        assertThat(outbox.sent()).extracting(MailMessage::to).containsExactlyInAnyOrder(ownerEmail, w);
    }

    @Test
    void recallWithinPeriodNotifiesRequestedSignersAndBlocksLinks() throws Exception {
        String no = template("SEQUENTIAL");
        String w = unique("w"), c = unique("c"), v = unique("v");
        String id = send(no, w, c, v);
        String tw = linkFor(w);
        outbox.clear();
        mvc.perform(post("/api/v1/documents/" + id + "/recall").header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RECALLED"))
            .andExpect(jsonPath("$.recalledAt").isString());
        assertThat(outbox.sent()).extracting(MailMessage::to).containsExactly(w);   // 대표는 아직 요청 전
        mvc.perform(post("/sign/api/session/link").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + tw + "\"}"))
            .andExpect(status().isGone());
        mvc.perform(post("/api/v1/documents/" + id + "/recall").header("Authorization", bearer))
            .andExpect(status().isConflict());
    }

    @Test
    void requestValidation() throws Exception {
        String no = template("PARALLEL");
        String w = unique("w"), c = unique("c");
        // 자리 빠짐
        request("{\"templateNo\":\"" + no + "\",\"signers\":[{\"slot\":\"worker\",\"email\":\"" + w + "\",\"name\":\"a\"}]}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("ceo")));
        // 없는 자리
        request(requestBody(no, w, c, unique("v")).replace("\"ceo\"", "\"boss\""))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("boss")));
        // 같은 이메일
        request(requestBody(no, w, w, unique("v"))).andExpect(status().isBadRequest());
        // 기간 31일 초과
        request(requestBody(no, w, c, unique("v")).replace("\"templateNo\"", "\"periodDays\":32,\"templateNo\""))
            .andExpect(status().isBadRequest());
        // 없는 템플릿
        request(requestBody("T-9999", w, c, unique("v"))).andExpect(status().isNotFound());
        // 준비 안 된 템플릿
        String draft = JsonPath.read(mvc.perform(multipart("/api/v1/templates")
                .file(new MockMultipartFile("file", "a.pdf", "application/pdf", pdf())).with(owner).with(csrf()))
            .andReturn().getResponse().getContentAsString(), "$.templateNo");
        request("{\"templateNo\":\"" + draft + "\",\"signers\":[{\"slot\":\"signer1\",\"email\":\"" + w + "\",\"name\":\"a\"}]}")
            .andExpect(status().isConflict());
        assertThat(outbox.sent()).isEmpty();
    }

    @Test
    void idempotencyKeyCreatesOnlyOnce() throws Exception {
        String no = template("PARALLEL");
        String body = requestBody(no, unique("w"), unique("c"), unique("v"));
        String a = JsonPath.read(mvc.perform(post("/api/v1/documents").header("Authorization", bearer)
                .header("Idempotency-Key", "order-123").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        int mails = outbox.sent().size();
        String b = JsonPath.read(mvc.perform(post("/api/v1/documents").header("Authorization", bearer)
                .header("Idempotency-Key", "order-123").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(b).isEqualTo(a);
        assertThat(outbox.sent()).hasSize(mails);
    }

    @Test
    void remindIsWebOnlyAndLimited() throws Exception {
        String no = template("SEQUENTIAL");
        String w = unique("w"), c = unique("c");
        String id = send(no, w, c, unique("v"));
        String detail = mvc.perform(get("/api/v1/documents/" + id).header("Authorization", bearer))
            .andReturn().getResponse().getContentAsString();
        String wid = JsonPath.read(detail, "$.signers[0].id");
        String cid = JsonPath.read(detail, "$.signers[1].id");
        String old = linkFor(w);

        mvc.perform(post("/api/v1/documents/" + id + "/recipients/" + wid + "/remind").header("Authorization", bearer))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/documents/" + id + "/recipients/" + wid + "/remind").with(owner).with(csrf()))
            .andExpect(status().isOk());
        assertThat(linkFor(w)).isNotEqualTo(old);
        mvc.perform(post("/api/v1/documents/" + id + "/recipients/" + wid + "/remind").with(owner).with(csrf()))
            .andExpect(status().isTooManyRequests());
        // 차례가 아닌 서명자에게는 보낼 수 없다
        mvc.perform(post("/api/v1/documents/" + id + "/recipients/" + cid + "/remind").with(owner).with(csrf()))
            .andExpect(status().isConflict());
    }

    @Test
    void listFiltersAndOwnership() throws Exception {
        String no = template("PARALLEL");
        String w = unique("w");
        String id = send(no, w, unique("c"), unique("v"));
        mvc.perform(get("/api/v1/documents").header("Authorization", bearer).param("q", w.substring(0, 10)))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(id))
            .andExpect(jsonPath("$[0].signerCount").value(2))
            .andExpect(jsonPath("$[0].currentSigner", containsString("홍길동")));
        mvc.perform(get("/api/v1/documents").header("Authorization", bearer).param("status", "COMPLETED"))
            .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/documents").header("Authorization", bearer).param("q", "없는검색어"))
            .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/documents").header("Authorization", bearer)
                .param("from", Instant.now().plusSeconds(3600).toString()))
            .andExpect(jsonPath("$.length()").value(0));

        String sub = "x-" + UUID.randomUUID();
        users.save(new AppUser(sub, sub + "@example.com", "남", Instant.now()));
        RequestPostProcessor other = oidcLogin().idToken(t -> t.subject(sub))
            .authorities(AuthorityUtils.createAuthorityList(Authorities.SESSION_AUTHORITIES));
        mvc.perform(get("/api/v1/documents/" + id).with(other)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/documents/" + id + "/recall").with(other).with(csrf())).andExpect(status().isNotFound());
        // 남의 템플릿 번호로는 요청할 수 없다 (번호는 요청자별)
    }

    @Test
    void editingTemplateAfterSendDoesNotChangeDocument() throws Exception {
        String no = template("PARALLEL");
        String id = send(no, unique("w"), unique("c"), unique("v"));
        mvc.perform(put("/api/v1/templates/" + no).with(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"name":"바뀐 이름","signingMode":"SEQUENTIAL","defaultPeriodDays":3,
                     "slots":[{"key":"worker","label":"근로자"}],
                     "fields":[{"slotKey":"worker","type":"SIGNATURE","page":1,"x":50,"y":700,"width":150,"height":50,"required":true}]}
                    """))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/documents/" + id).header("Authorization", bearer))
            .andExpect(jsonPath("$.title").value("근로계약서"))
            .andExpect(jsonPath("$.signingMode").value("PARALLEL"))
            .andExpect(jsonPath("$.templateRevision").value(2))
            .andExpect(jsonPath("$.signers.length()").value(2))
            .andExpect(jsonPath("$.fields.length()").value(4));
    }

    @Test
    void readOnlyTokenCannotRequest() throws Exception {
        String no = template("PARALLEL");
        String body = mvc.perform(post("/api/v1/me/tokens").with(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ro\",\"scopes\":[\"documents:read\"]}"))
            .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/documents").header("Authorization", "Bearer " + JsonPath.read(body, "$.token"))
                .contentType(MediaType.APPLICATION_JSON).content(requestBody(no, unique("w"), unique("c"), unique("v"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void otpLocksAfterFiveWrongCodes() throws Exception {
        String no = template("PARALLEL");
        String w = unique("w");
        send(no, w, unique("c"), unique("v"));
        String token = linkFor(w);
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/sign/api/session/otp").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
            .andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            verify(s, token, "000000").andExpect(status().is4xxClientError());
        }
        verify(s, token, "000000").andExpect(status().is4xxClientError());
        mvc.perform(get("/sign/api/request").session(s)).andExpect(status().isUnauthorized());
    }

    private ResultActions verify(MockHttpSession s, String token, String code) throws Exception {
        return mvc.perform(post("/sign/api/session/verify").session(s).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\",\"code\":\"" + code + "\"}"));
    }

    /** 완료 PDF를 다시 렌더링해, 입력값이 칸 위치(90도 회전한 페이지 포함)에 실제로 그려졌는지 픽셀로 확인한다. */
    @Test
    void flattenedPdfDrawsAtFieldPositionsIncludingRotatedPage() throws Exception {
        byte[] twoPages;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            PDPage rotated = new PDPage(PDRectangle.A4);
            rotated.setRotation(90);
            doc.addPage(rotated);
            doc.save(out);
            twoPages = out.toByteArray();
        }
        String no = JsonPath.read(mvc.perform(multipart("/api/v1/templates")
                .file(new MockMultipartFile("file", "r.pdf", "application/pdf", twoPages)).with(owner).with(csrf()))
            .andReturn().getResponse().getContentAsString(), "$.templateNo");
        mvc.perform(put("/api/v1/templates/" + no).with(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"name":"회전","signingMode":"PARALLEL","defaultPeriodDays":14,
                     "slots":[{"key":"signer1","label":"서명자"}],
                     "fields":[
                       {"slotKey":"signer1","type":"SIGNATURE","page":1,"x":100,"y":600,"width":150,"height":50,"required":true},
                       {"slotKey":"signer1","type":"DATE","page":2,"x":500,"y":400,"width":120,"height":30,"required":true},
                       {"type":"TEXT_FIXED","page":2,"x":100,"y":100,"width":200,"height":30,"fixedValue":"고정값"}]}
                    """))
            .andExpect(status().isOk());
        String w = unique("w");
        String id = JsonPath.read(request("{\"templateNo\":\"" + no + "\",\"signers\":[{\"slot\":\"signer1\",\"email\":\""
                + w + "\",\"name\":\"서명자\"}]}")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        signAll(login(linkFor(w), w));
        byte[] result = mvc.perform(get("/api/v1/documents/" + id + "/result").header("Authorization", bearer))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument out = Loader.loadPDF(result)) {
            org.apache.pdfbox.rendering.PDFRenderer r = new org.apache.pdfbox.rendering.PDFRenderer(out);
            BufferedImage p1 = r.renderImage(0, 1f);                // 1pt = 1px
            assertThat(dark(p1, 100, 600, 150, 50)).as("signature on page 1").isGreaterThan(5000);
            assertThat(dark(p1, 300, 600, 150, 50)).as("blank beside signature").isZero();
            BufferedImage p2 = r.renderImage(1, 1f);
            assertThat(p2.getWidth()).isGreaterThan(p2.getHeight()); // 회전 반영
            assertThat(dark(p2, 500, 400, 120, 30)).as("date on rotated page").isGreaterThan(20);
            assertThat(dark(p2, 100, 100, 200, 30)).as("fixed text on rotated page").isGreaterThan(20);
            assertThat(dark(p2, 300, 250, 120, 30)).as("blank area on rotated page").isZero();
        }
    }

    static int dark(BufferedImage img, int x, int y, int w, int h) {
        int n = 0;
        for (int i = x; i < x + w; i++) {
            for (int j = y; j < y + h; j++) {
                Color c = new Color(img.getRGB(i, j));
                if (c.getRed() + c.getGreen() + c.getBlue() < 384) {
                    n++;
                }
            }
        }
        return n;
    }

    @Test
    void missingCsrfOnRecipientApiIs403() throws Exception {
        mvc.perform(post("/sign/api/session/link").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"x\"}"))
            .andExpect(status().isForbidden());
    }
}
