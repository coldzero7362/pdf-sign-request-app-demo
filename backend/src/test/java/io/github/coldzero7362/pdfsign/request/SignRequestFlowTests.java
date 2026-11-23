package io.github.coldzero7362.pdfsign.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.coldzero7362.pdfsign.auth.Authorities;
import io.github.coldzero7362.pdfsign.user.AppUser;
import io.github.coldzero7362.pdfsign.user.AppUserRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class SignRequestFlowTests {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;

    @BeforeEach
    void users() {
        users.save(new AppUser("owner-sub", "owner@example.com", "요청자", Instant.now()));
        users.save(new AppUser("other-sub", "other@example.com", "다른 사람", Instant.now()));
    }

    static RequestPostProcessor as(String sub) {
        return oidcLogin().idToken(t -> t.subject(sub))
            .authorities(AuthorityUtils.createAuthorityList(Authorities.SESSION_AUTHORITIES));
    }

    static final RequestPostProcessor OWNER = as("owner-sub");
    static final RequestPostProcessor OTHER = as("other-sub");

    // ---------- 테스트용 PDF ----------

    /** A4 세로 1쪽 + A4를 90도 돌린 1쪽. */
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

    static byte[] encryptedPdf() throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner", "user", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            doc.save(out);
            return out.toByteArray();
        }
    }

    String upload(RequestPostProcessor who) throws Exception {
        String body = mvc.perform(multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", "계약서.pdf", "application/pdf", samplePdf()))
                .with(who).with(csrf()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    String createRequest(String documentId) throws Exception {
        String body = mvc.perform(post("/api/v1/requests").with(OWNER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\":\"" + documentId + "\",\"title\":\"근로계약서 서명\",\"message\":\"확인 부탁드립니다\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    static final String LAYOUT = """
        {"recipients":[
           {"key":"a","email":"signer@example.com","name":"서명자","role":"SIGNER"},
           {"key":"b","email":"viewer@example.com","name":"열람자","role":"VIEWER"}],
         "fields":[
           {"recipientKey":"a","type":"TEXT","page":1,"x":50,"y":100,"width":200,"height":24,"required":true,"label":"이름"},
           {"recipientKey":"a","type":"SIGNATURE","page":2,"x":600,"y":400,"width":150,"height":60,"required":true}]}
        """;

    // ---------- 문서 ----------

    @Test
    void uploadReadsPagesIncludingRotation() throws Exception {
        mvc.perform(multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", "../../etc/계약서.pdf", "application/pdf", samplePdf()))
                .with(OWNER).with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fileName").value("계약서.pdf"))
            .andExpect(jsonPath("$.pageCount").value(2))
            .andExpect(jsonPath("$.pages[0].width").value(595.28))
            .andExpect(jsonPath("$.pages[0].height").value(841.89))
            // 90도 회전한 페이지는 가로·세로가 바뀐다
            .andExpect(jsonPath("$.pages[1].width").value(841.89))
            .andExpect(jsonPath("$.pages[1].rotation").value(90))
            .andExpect(jsonPath("$.sha256").isString());
    }

    @Test
    void nonPdfIsRejected() throws Exception {
        mvc.perform(multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", "x.pdf", "application/pdf", "<script>alert(1)</script>".getBytes()))
                .with(OWNER).with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("PDF 파일이 아닙니다."));
    }

    @Test
    void encryptedPdfIsRejected() throws Exception {
        mvc.perform(multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", "secret.pdf", "application/pdf", encryptedPdf()))
                .with(OWNER).with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void downloadIsSandboxedAndOwnerOnly() throws Exception {
        String doc = upload(OWNER);
        byte[] pdf = mvc.perform(get("/api/v1/documents/" + doc + "/file").with(OWNER))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Security-Policy", "sandbox"))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        mvc.perform(get("/api/v1/documents/" + doc + "/file").with(OTHER)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documents/" + doc).with(OTHER)).andExpect(status().isNotFound());
    }

    // ---------- 요청 ----------

    @Test
    void fullDraftToSendToCancelFlow() throws Exception {
        String id = createRequest(upload(OWNER));

        mvc.perform(put("/api/v1/requests/" + id + "/layout").with(OWNER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(LAYOUT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recipients.length()").value(2))
            .andExpect(jsonPath("$.fields.length()").value(2))
            .andExpect(jsonPath("$.recipients[0].status").value("PENDING"));

        mvc.perform(get("/api/v1/requests").with(OWNER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].documentName").value("계약서.pdf"))
            .andExpect(jsonPath("$[0].signerCount").value(1));

        mvc.perform(post("/api/v1/requests/" + id + "/send").with(OWNER).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SENT"))
            .andExpect(jsonPath("$.sentAt").isString())
            .andExpect(jsonPath("$.recipients[0].status").value("SENT"));

        // 발송 후에는 수정할 수 없다
        mvc.perform(put("/api/v1/requests/" + id + "/layout").with(OWNER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(LAYOUT))
            .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/requests/" + id).with(OWNER).with(csrf()))
            .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/requests/" + id + "/cancel").with(OWNER).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELED"));

        String audit = mvc.perform(get("/api/v1/requests/" + id + "/audit").with(OWNER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<String> types = JsonPath.read(audit, "$[*].type");
        assertThat(types).containsExactly("CREATED", "SENT", "CANCELED");
    }

    @Test
    void sendRequiresSignerWithFields() throws Exception {
        String id = createRequest(upload(OWNER));
        mvc.perform(post("/api/v1/requests/" + id + "/send").with(OWNER).with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail", containsString("서명자")));

        mvc.perform(put("/api/v1/requests/" + id + "/layout").with(OWNER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipients\":[{\"key\":\"a\",\"email\":\"s@example.com\",\"name\":\"s\",\"role\":\"SIGNER\"}],\"fields\":[]}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/requests/" + id + "/send").with(OWNER).with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail", containsString("입력 칸이 없는")));
    }

    @Test
    void layoutValidation() throws Exception {
        String id = createRequest(upload(OWNER));
        String base = "{\"recipients\":[{\"key\":\"a\",\"email\":\"s@example.com\",\"name\":\"s\",\"role\":\"%s\"}%s],\"fields\":[%s]}";
        String okField = "{\"recipientKey\":\"a\",\"type\":\"TEXT\",\"page\":1,\"x\":10,\"y\":10,\"width\":100,\"height\":20,\"required\":true}";

        // 페이지 밖 (A4 폭 595pt)
        assertBad(id, base.formatted("SIGNER", "",
            "{\"recipientKey\":\"a\",\"type\":\"TEXT\",\"page\":1,\"x\":550,\"y\":10,\"width\":100,\"height\":20,\"required\":true}"));
        // 없는 페이지
        assertBad(id, base.formatted("SIGNER", "",
            "{\"recipientKey\":\"a\",\"type\":\"TEXT\",\"page\":3,\"x\":10,\"y\":10,\"width\":100,\"height\":20,\"required\":true}"));
        // 너무 작은 칸
        assertBad(id, base.formatted("SIGNER", "",
            "{\"recipientKey\":\"a\",\"type\":\"TEXT\",\"page\":1,\"x\":10,\"y\":10,\"width\":2,\"height\":2,\"required\":true}"));
        // 열람자에게 칸
        assertBad(id, base.formatted("VIEWER", "", okField));
        // 이메일 중복 (대소문자 무시)
        assertBad(id, base.formatted("SIGNER",
            ",{\"key\":\"b\",\"email\":\"S@example.com\",\"name\":\"t\",\"role\":\"SIGNER\"}", okField));
        // 잘못된 이메일
        assertBad(id, "{\"recipients\":[{\"key\":\"a\",\"email\":\"not-an-email\",\"name\":\"s\",\"role\":\"SIGNER\"}],\"fields\":[]}");
    }

    private void assertBad(String id, String json) throws Exception {
        mvc.perform(put("/api/v1/requests/" + id + "/layout").with(OWNER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isBadRequest());
    }

    @Test
    void otherUsersCannotSeeOrUseMyRequestsOrDocuments() throws Exception {
        String doc = upload(OWNER);
        String id = createRequest(doc);

        mvc.perform(get("/api/v1/requests/" + id).with(OTHER)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/requests/" + id + "/send").with(OTHER).with(csrf())).andExpect(status().isNotFound());
        // 남의 문서로 요청을 만들 수도 없다
        mvc.perform(post("/api/v1/requests").with(OTHER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\":\"" + doc + "\",\"title\":\"x\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/requests").with(OTHER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void readOnlyTokenCannotWrite() throws Exception {
        String body = mvc.perform(post("/api/v1/me/tokens").with(OWNER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ro\",\"scopes\":[\"requests:read\"]}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");

        mvc.perform(get("/api/v1/requests").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mvc.perform(multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", "a.pdf", "application/pdf", samplePdf()))
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void writeTokenCanDoTheWholeFlowWithoutCsrf() throws Exception {
        String body = mvc.perform(post("/api/v1/me/tokens").with(OWNER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"rw\",\"scopes\":[\"requests:read\",\"requests:write\"]}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + JsonPath.read(body, "$.token");

        String docBody = mvc.perform(multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", "a.pdf", "application/pdf", samplePdf()))
                .header("Authorization", bearer))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String reqBody = mvc.perform(post("/api/v1/requests").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\":\"" + JsonPath.read(docBody, "$.id") + "\",\"title\":\"api\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(reqBody, "$.id");
        mvc.perform(put("/api/v1/requests/" + id + "/layout").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(LAYOUT))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/requests/" + id + "/send").header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    void draftCanBeDeleted() throws Exception {
        String id = createRequest(upload(OWNER));
        mvc.perform(put("/api/v1/requests/" + id + "/layout").with(OWNER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(LAYOUT))
            .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/requests/" + id).with(OWNER).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/requests/" + id).with(OWNER)).andExpect(status().isNotFound());
    }

    @Test
    void expiryMustBeWithin90Days() throws Exception {
        String doc = upload(OWNER);
        mvc.perform(post("/api/v1/requests").with(OWNER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\":\"" + doc + "\",\"title\":\"x\",\"expiresAt\":\"2020-01-01T00:00:00Z\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/requests").with(OWNER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\":\"" + doc + "\",\"title\":\"x\",\"expiresAt\":\""
                    + Instant.now().plusSeconds(200L * 24 * 3600) + "\"}"))
            .andExpect(status().isBadRequest());
    }
}
