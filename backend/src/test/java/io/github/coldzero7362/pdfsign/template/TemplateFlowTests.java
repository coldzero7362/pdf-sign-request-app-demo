package io.github.coldzero7362.pdfsign.template;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
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
class TemplateFlowTests {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;

    static RequestPostProcessor as(String sub) {
        return oidcLogin().idToken(t -> t.subject(sub))
            .authorities(AuthorityUtils.createAuthorityList(Authorities.SESSION_AUTHORITIES));
    }

    static final RequestPostProcessor OWNER = as("tpl-owner");
    static final RequestPostProcessor OTHER = as("tpl-other");

    @BeforeEach
    void users() {
        users.save(new AppUser("tpl-owner", "owner@example.com", "요청자", Instant.now()));
        users.save(new AppUser("tpl-other", "other@example.com", "남", Instant.now()));
    }

    static byte[] pdf() throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            doc.addPage(new PDPage(PDRectangle.A4));
            doc.save(out);
            return out.toByteArray();
        }
    }

    String create(RequestPostProcessor who, String name) throws Exception {
        String body = mvc.perform(multipart("/api/v1/templates")
                .file(new MockMultipartFile("file", "근로계약서.pdf", "application/pdf", pdf()))
                .param("name", name).with(who).with(csrf()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.templateNo");
    }

    static final String VALID = """
        {"name":"근로계약서","signingMode":"SEQUENTIAL","defaultPeriodDays":14,
         "slots":[{"key":"signer1","label":"근로자"},{"key":"signer2","label":"대표"}],
         "fields":[
           {"slotKey":"signer1","type":"SIGNATURE","page":1,"x":50,"y":700,"width":150,"height":50,"required":true},
           {"slotKey":"signer1","type":"TEXT","page":1,"x":50,"y":100,"width":200,"height":24,"required":true,"label":"성명"},
           {"slotKey":"signer2","type":"DATE","page":2,"x":300,"y":700,"width":120,"height":24,"required":true},
           {"type":"TEXT_FIXED","page":1,"x":50,"y":60,"width":300,"height":24,"fixedValue":"(주)유사인 근로계약서"}]}
        """;

    @Test
    void numbersArePerOwnerAndStartWithOneSignerSlot() throws Exception {
        String a = create(OWNER, "첫 번째");
        String b = create(OWNER, "두 번째");
        String c = create(OTHER, "남의 첫 번째");
        org.assertj.core.api.Assertions.assertThat(a).isEqualTo("T-0001");
        org.assertj.core.api.Assertions.assertThat(b).isEqualTo("T-0002");
        org.assertj.core.api.Assertions.assertThat(c).isEqualTo("T-0001");

        mvc.perform(get("/api/v1/templates/" + a).with(OWNER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.signingMode").value("SEQUENTIAL"))
            .andExpect(jsonPath("$.defaultPeriodDays").value(14))
            .andExpect(jsonPath("$.slots[0].key").value("signer1"))
            .andExpect(jsonPath("$.ready").value(false))
            .andExpect(jsonPath("$.problems[0]", containsString("입력 칸이 없습니다")))
            .andExpect(jsonPath("$.pdf.pageCount").value(2));
    }

    @Test
    void saveReplacesDefinitionAndBumpsRevision() throws Exception {
        String no = create(OWNER, "근로계약서");
        mvc.perform(put("/api/v1/templates/" + no).with(OWNER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revision").value(2))
            .andExpect(jsonPath("$.ready").value(true))
            .andExpect(jsonPath("$.slots.length()").value(2))
            .andExpect(jsonPath("$.slots[1].order").value(2))
            .andExpect(jsonPath("$.slots[0].fieldCount").value(2))
            .andExpect(jsonPath("$.fields.length()").value(4))
            .andExpect(jsonPath("$.fields[?(@.type=='TEXT_FIXED')].slotKey").value(hasItem((Object) null)))
            .andExpect(jsonPath("$.fields[?(@.type=='TEXT_FIXED')].required").value(hasItem(false)))
            // API 사용자 안내용 요청 예시
            .andExpect(jsonPath("$.requestExample.templateNo").value(no))
            .andExpect(jsonPath("$.requestExample.signers[1].slot").value("signer2"))
            .andExpect(jsonPath("$.requestExample.viewers[0].email").isString());

        mvc.perform(get("/api/v1/templates").with(OWNER))
            .andExpect(jsonPath("$[0].templateNo").value(no))
            .andExpect(jsonPath("$[0].ready").value(true))
            .andExpect(jsonPath("$[0].slotCount").value(2));
    }

    @Test
    void validation() throws Exception {
        String no = create(OWNER, "x");
        String head = "{\"name\":\"x\",\"signingMode\":\"PARALLEL\",\"defaultPeriodDays\":%d,\"slots\":[{\"key\":\"signer1\",\"label\":\"a\"}%s],\"fields\":[%s]}";
        String ok = "{\"slotKey\":\"signer1\",\"type\":\"SIGNATURE\",\"page\":1,\"x\":10,\"y\":10,\"width\":100,\"height\":40,\"required\":true}";
        // 기간 31일 초과
        bad(no, head.formatted(32, "", ok));
        // 자리 키 중복
        bad(no, head.formatted(14, ",{\"key\":\"signer1\",\"label\":\"b\"}", ok));
        // 잘못된 자리 키 형식
        bad(no, "{\"name\":\"x\",\"signingMode\":\"PARALLEL\",\"defaultPeriodDays\":14,\"slots\":[{\"key\":\"Signer 1\",\"label\":\"a\"}],\"fields\":[]}");
        // 없는 자리를 가리키는 칸
        bad(no, head.formatted(14, "", ok.replace("signer1", "signer9")));
        // 고정 텍스트에 값 없음
        bad(no, head.formatted(14, "", "{\"type\":\"TEXT_FIXED\",\"page\":1,\"x\":10,\"y\":10,\"width\":100,\"height\":20}"));
        // 서명 칸에 고정 값
        bad(no, head.formatted(14, "", ok.replace("\"required\":true", "\"required\":true,\"fixedValue\":\"x\"")));
        // 페이지 밖
        bad(no, head.formatted(14, "", ok.replace("\"x\":10", "\"x\":560")));
        // 없는 페이지
        bad(no, head.formatted(14, "", ok.replace("\"page\":1", "\"page\":3")));
        // 자리 0개
        bad(no, "{\"name\":\"x\",\"signingMode\":\"PARALLEL\",\"defaultPeriodDays\":14,\"slots\":[],\"fields\":[]}");
    }

    private void bad(String no, String json) throws Exception {
        mvc.perform(put("/api/v1/templates/" + no).with(OWNER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isBadRequest());
    }

    @Test
    void apiTokenCanOnlyRead() throws Exception {
        String no = create(OWNER, "x");
        String body = mvc.perform(post("/api/v1/me/tokens").with(OWNER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"t\",\"scopes\":[\"templates:read\"]}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + JsonPath.read(body, "$.token");

        mvc.perform(get("/api/v1/templates").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/templates/" + no).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/templates/" + no + "/pdf").header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Security-Policy", "sandbox"));
        // 만들기·수정·삭제는 웹 세션만
        mvc.perform(put("/api/v1/templates/" + no).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/templates")
                .file(new MockMultipartFile("file", "a.pdf", "application/pdf", pdf()))
                .header("Authorization", bearer))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/templates/" + no).header("Authorization", bearer))
            .andExpect(status().isForbidden());

        // 조회 스코프가 없는 토큰은 템플릿을 볼 수 없다
        String body2 = mvc.perform(post("/api/v1/me/tokens").with(OWNER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"r\",\"scopes\":[\"documents:read\"]}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/templates").header("Authorization", "Bearer " + JsonPath.read(body2, "$.token")))
            .andExpect(status().isForbidden());
    }

    @Test
    void otherUsersCannotSeeAndDeletedTemplatesDisappear() throws Exception {
        String no = create(OWNER, "x");
        mvc.perform(get("/api/v1/templates/" + no).with(OTHER)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/templates/" + no).with(OTHER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/templates/" + no).with(OWNER).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/templates/" + no).with(OWNER)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/templates").with(OWNER)).andExpect(jsonPath("$.length()").value(0));
        // 삭제해도 번호는 다시 쓰지 않는다
        org.assertj.core.api.Assertions.assertThat(create(OWNER, "y")).isEqualTo("T-0002");
    }

    @Test
    void nonPdfIsRejected() throws Exception {
        mvc.perform(multipart("/api/v1/templates")
                .file(new MockMultipartFile("file", "a.pdf", "application/pdf", "hello".getBytes()))
                .with(OWNER).with(csrf()))
            .andExpect(status().isBadRequest());
    }
}
