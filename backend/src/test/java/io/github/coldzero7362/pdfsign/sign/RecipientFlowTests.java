package io.github.coldzero7362.pdfsign.sign;

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
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 수신자 흐름 전체. 메일은 커밋 뒤에 나가므로 이 테스트는 트랜잭션 롤백 없이 돈다 (매번 새 사용자).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RecipientFlowTests {

    static final Pattern LINK = Pattern.compile("/sign/([A-Za-z0-9_-]{43})");
    static final Pattern CODE = Pattern.compile("인증번호: (\\d{6})");

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("appMailSender") MailSender mailSender;

    LoggingMailSender outbox;
    RequestPostProcessor owner;
    String ownerEmail;

    @BeforeEach
    void setUp() {
        outbox = (LoggingMailSender) mailSender;
        outbox.clear();
        String sub = "owner-" + UUID.randomUUID();
        ownerEmail = sub + "@example.com";
        users.save(new AppUser(sub, ownerEmail, "김요청", Instant.now()));
        owner = oidcLogin().idToken(t -> t.subject(sub))
            .authorities(AuthorityUtils.createAuthorityList(Authorities.SESSION_AUTHORITIES));
    }

    // ---------- 준비 ----------

    static byte[] samplePdf() throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            PDPage rotated = new PDPage(PDRectangle.A4);
            rotated.setRotation(90);
            doc.addPage(rotated);
            doc.save(out);
            return out.toByteArray();
        }
    }

    static byte[] signaturePng() throws IOException {
        BufferedImage img = new BufferedImage(300, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 300, 100); // 확인하기 쉽게 꽉 채운다
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** 서명자 A(칸 4개, 2쪽은 회전), 서명자 B(칸 1개), 열람자 V. 발송까지. */
    String sendRequest(String a, String b, String v) throws Exception {
        String doc = JsonPath.read(mvc.perform(multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", "계약서.pdf", "application/pdf", samplePdf()))
                .with(owner).with(csrf()))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String id = JsonPath.read(mvc.perform(post("/api/v1/requests").with(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\":\"" + doc + "\",\"title\":\"근로계약서 <b>서명</b>\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/v1/requests/" + id + "/layout").with(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"recipients":[
                       {"key":"a","email":"%s","name":"홍길동","role":"SIGNER"},
                       {"key":"b","email":"%s","name":"김철수","role":"SIGNER"},
                       {"key":"v","email":"%s","name":"인사팀","role":"VIEWER"}],
                     "fields":[
                       {"recipientKey":"a","type":"TEXT","page":1,"x":100,"y":100,"width":200,"height":30,"required":true},
                       {"recipientKey":"a","type":"CHECKBOX","page":1,"x":100,"y":200,"width":20,"height":20,"required":false},
                       {"recipientKey":"a","type":"SIGNATURE","page":1,"x":100,"y":600,"width":150,"height":50,"required":true},
                       {"recipientKey":"a","type":"DATE","page":2,"x":500,"y":400,"width":120,"height":30,"required":true},
                       {"recipientKey":"b","type":"TEXT","page":1,"x":350,"y":100,"width":150,"height":30,"required":true}]}
                    """.formatted(a, b, v)))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/requests/" + id + "/send").with(owner).with(csrf()))
            .andExpect(status().isOk());
        return id;
    }

    String linkFor(String email) {
        List<MailMessage> mine = outbox.sent().stream().filter(m -> m.to().equals(email)).toList();
        assertThat(mine).as("mail to " + email).isNotEmpty();
        Matcher m = LINK.matcher(mine.getLast().text());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    String lastCode(String email) {
        MailMessage last = outbox.sent().stream().filter(m -> m.to().equals(email))
            .filter(m -> m.subject().startsWith("[인증번호]")).toList().getLast();
        Matcher m = CODE.matcher(last.text());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    MockHttpSession login(String token, String email) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/sign/api/session/otp").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/sign/api/session/verify").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"code\":\"" + lastCode(email) + "\"}"))
            .andExpect(status().isNoContent());
        return session;
    }

    List<String> fieldIds(MockHttpSession s) throws Exception {
        return JsonPath.read(mvc.perform(get("/sign/api/request").session(s))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.fields[*].id");
    }

    String unique(String name) {
        return name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    // ---------- 테스트 ----------

    @Test
    void fullFlowProducesFlattenedPdfAndNotifiesEveryone() throws Exception {
        String a = unique("a"), b = unique("b"), v = unique("v");
        String id = sendRequest(a, b, v);

        // 초대 메일은 서명자에게만, 제목의 HTML은 이스케이프된다
        assertThat(outbox.sent()).extracting(MailMessage::to).containsExactlyInAnyOrder(a, b);
        MailMessage invite = outbox.sent().getFirst();
        assertThat(invite.html()).contains("근로계약서 &lt;b&gt;서명&lt;/b&gt;").doesNotContain("<b>서명</b>");
        String tokenA = linkFor(a);

        // 링크 정보: 인증 전에는 문서 내용 없이 최소 정보만
        MockHttpSession sa = new MockHttpSession();
        mvc.perform(post("/sign/api/session/link").session(sa).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + tokenA + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.senderName").value("김요청"))
            .andExpect(jsonPath("$.maskedEmail").value(a.substring(0, 2) + "***@example.com"))
            .andExpect(jsonPath("$.verified").value(false));
        // 인증 전에는 문서에 접근할 수 없다
        mvc.perform(get("/sign/api/request").session(sa)).andExpect(status().isUnauthorized());

        // OTP: 받기 → 곧바로 다시 받기는 막힘 → 틀린 번호 → 맞는 번호
        mvc.perform(post("/sign/api/session/otp").session(sa).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + tokenA + "\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/sign/api/session/otp").session(sa).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + tokenA + "\"}"))
            .andExpect(status().isTooManyRequests());
        String code = lastCode(a);
        String wrong = code.equals("000000") ? "111111" : "000000";
        mvc.perform(post("/sign/api/session/verify").session(sa).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + tokenA + "\",\"code\":\"" + wrong + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail", containsString("남은 시도 4번")));
        String before = sa.getId();
        mvc.perform(post("/sign/api/session/verify").session(sa).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + tokenA + "\",\"code\":\"" + code + "\"}"))
            .andExpect(status().isNoContent());
        assertThat(sa.getId()).as("session id rotated").isNotEqualTo(before);

        // 작성 화면: 내 칸 4개, 첫 열람으로 진행 중
        mvc.perform(get("/sign/api/request").session(sa))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fields.length()").value(4))
            .andExpect(jsonPath("$.recipientStatus").value("VIEWED"))
            .andExpect(jsonPath("$.requestStatus").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.canEdit").value(true));
        List<String> ids = fieldIds(sa);
        // 정렬: 1쪽(y 100 텍스트, y 200 체크, y 600 서명), 2쪽 날짜
        String text = ids.get(0), check = ids.get(1), sig = ids.get(2), date = ids.get(3);

        mvc.perform(post("/sign/api/submit").session(sa).with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail", containsString("필수 칸 3개")));

        fill(sa, text, "홍길동").andExpect(status().isOk()).andExpect(jsonPath("$.filled").value(true));
        fill(sa, check, "true").andExpect(status().isOk());
        fill(sa, date, "23-11-2026").andExpect(status().isBadRequest());
        fill(sa, date, "2026-11-23").andExpect(status().isOk());
        fill(sa, sig, "글자").andExpect(status().isBadRequest());
        mvc.perform(multipart("/sign/api/fields/" + sig + "/image")
                .file(new MockMultipartFile("file", "x.png", "image/png", "<svg onload=alert(1)>".getBytes()))
                .session(sa).with(csrf()))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart("/sign/api/fields/" + sig + "/image")
                .file(new MockMultipartFile("file", "sign.png", "image/png", signaturePng()))
                .session(sa).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasImage").value(true));
        // CSRF 없이 바꾸기는 막힌다
        mvc.perform(put("/sign/api/fields/" + text).session(sa)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"x\"}"))
            .andExpect(status().isForbidden());

        mvc.perform(post("/sign/api/submit").session(sa).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recipientStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.requestStatus").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.canEdit").value(false));
        fill(sa, text, "바꾸기").andExpect(status().isConflict());

        // B: 다른 사람의 칸은 건드릴 수 없다
        MockHttpSession sb = login(linkFor(b), b);
        fill(sb, text, "가로채기").andExpect(status().isNotFound());
        String bText = fieldIds(sb).getFirst();
        fill(sb, bText, "김철수").andExpect(status().isOk());
        outbox.clear();
        mvc.perform(post("/sign/api/submit").session(sb).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.resultAvailable").value(true));

        // 완료 메일: 서명자 둘, 열람자, 요청자
        assertThat(outbox.sent()).extracting(MailMessage::to).containsExactlyInAnyOrder(a, b, v, ownerEmail);

        // 요청자: 완료본 확인
        byte[] pdf = mvc.perform(get("/api/v1/requests/" + id + "/result").with(owner))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        mvc.perform(get("/api/v1/requests/" + id).with(owner))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.resultSha256").isString());
        try (PDDocument out = Loader.loadPDF(pdf)) {
            assertThat(out.getNumberOfPages()).isGreaterThanOrEqualTo(3);
            String all = new PDFTextStripper().getText(out);
            assertThat(all).contains("홍길동", "김철수", "2026-11-23", "감사 기록", "본인 확인", "작성 완료", "전체 완료");

            PDFRenderer renderer = new PDFRenderer(out);
            // 1쪽: 서명 이미지(검정으로 꽉 참) 자리가 어둡고, 그 옆 빈 곳은 희다 (1pt = 1px)
            BufferedImage p1 = renderer.renderImage(0, 1f);
            assertThat(dark(p1, 175, 625)).as("signature area").isTrue();
            assertThat(dark(p1, 400, 625)).as("blank area").isFalse();
            // 2쪽(90도 회전): 화면 좌표 (500,400)~(620,430)에 날짜 글자가 있어야 한다
            BufferedImage p2 = renderer.renderImage(1, 1f);
            assertThat(p2.getWidth()).isGreaterThan(p2.getHeight());
            assertThat(darkPixels(p2, 500, 400, 120, 30)).as("date text on rotated page").isGreaterThan(20);
            assertThat(darkPixels(p2, 100, 100, 120, 30)).as("blank area on rotated page").isZero();
        }

        // 열람자: 완료 링크 → OTP → 완료본
        MockHttpSession sv = login(linkFor(v), v);
        mvc.perform(get("/sign/api/request").session(sv))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("VIEWER"))
            .andExpect(jsonPath("$.fields.length()").value(0));
        mvc.perform(get("/sign/api/result").session(sv)).andExpect(status().isOk());

        // 처음 받은 초대 링크는 완료 때 새 링크로 바뀌어 더는 쓸 수 없다
        mvc.perform(post("/sign/api/session/link").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + tokenA + "\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void declineClosesRequestAndNotifiesOwner() throws Exception {
        String a = unique("a"), b = unique("b"), v = unique("v");
        String id = sendRequest(a, b, v);
        String tokenA = linkFor(a);
        String tokenB = linkFor(b);
        MockHttpSession sb = login(tokenB, b);
        outbox.clear();
        mvc.perform(post("/sign/api/decline").session(sb).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"조건 재협의 필요\"}"))
            .andExpect(status().isNoContent());
        assertThat(outbox.sent()).extracting(MailMessage::to).containsExactly(ownerEmail);
        assertThat(outbox.sent().getFirst().text()).contains("조건 재협의 필요");
        mvc.perform(get("/api/v1/requests/" + id).with(owner))
            .andExpect(jsonPath("$.status").value("DECLINED"))
            .andExpect(jsonPath("$.recipients[1].status").value("DECLINED"))
            .andExpect(jsonPath("$.recipients[1].declineReason").value("조건 재협의 필요"));
        // 다른 서명자도 더는 들어올 수 없다
        mvc.perform(post("/sign/api/session/link").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + tokenA + "\"}"))
            .andExpect(status().isGone());
    }

    @Test
    void cancelBlocksLinksAndTellsSigners() throws Exception {
        String a = unique("a"), b = unique("b"), v = unique("v");
        String id = sendRequest(a, b, v);
        String tokenA = linkFor(a);
        MockHttpSession sa = login(tokenA, a);
        outbox.clear();
        mvc.perform(post("/api/v1/requests/" + id + "/cancel").with(owner).with(csrf()))
            .andExpect(status().isOk());
        assertThat(outbox.sent()).extracting(MailMessage::to).containsExactlyInAnyOrder(a, b);
        mvc.perform(get("/sign/api/request").session(sa)).andExpect(status().isGone());
        mvc.perform(post("/sign/api/session/link").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + tokenA + "\"}"))
            .andExpect(status().isGone());
    }

    @Test
    void otpLocksAfterFiveWrongCodes() throws Exception {
        String a = unique("a"), b = unique("b"), v = unique("v");
        sendRequest(a, b, v);
        String token = linkFor(a);
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/sign/api/session/otp").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
            .andExpect(status().isOk());
        String code = lastCode(a);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 4; i++) {
            verify(s, token, wrong).andExpect(status().isBadRequest());
        }
        verify(s, token, wrong).andExpect(status().isTooManyRequests());
        // 이제 맞는 번호도 통하지 않는다 (새로 받아야 함)
        verify(s, token, code).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail", containsString("다시 받아")));
    }

    @Test
    void missingCsrfIs403NotLoginRedirect() throws Exception {
        mvc.perform(post("/sign/api/session/link")
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"x\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void unknownTokenIs404() throws Exception {
        mvc.perform(post("/sign/api/session/link").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"nope\"}"))
            .andExpect(status().isNotFound());
    }

    // ---------- 도우미 ----------

    org.springframework.test.web.servlet.ResultActions fill(MockHttpSession s, String fieldId, String value) throws Exception {
        return mvc.perform(put("/sign/api/fields/" + fieldId).session(s).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"" + value + "\"}"));
    }

    org.springframework.test.web.servlet.ResultActions verify(MockHttpSession s, String token, String code) throws Exception {
        return mvc.perform(post("/sign/api/session/verify").session(s).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\",\"code\":\"" + code + "\"}"));
    }

    static boolean dark(BufferedImage img, int x, int y) {
        Color c = new Color(img.getRGB(x, y));
        return c.getRed() + c.getGreen() + c.getBlue() < 200;
    }

    static int darkPixels(BufferedImage img, int x, int y, int w, int h) {
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
}
