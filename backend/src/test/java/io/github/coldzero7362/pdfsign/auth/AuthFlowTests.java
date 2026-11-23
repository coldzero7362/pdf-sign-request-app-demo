package io.github.coldzero7362.pdfsign.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.coldzero7362.pdfsign.user.AppUser;
import io.github.coldzero7362.pdfsign.user.AppUserRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AuthFlowTests {

    static final String SUB = "google-sub-1";

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;

    @BeforeEach
    void user() {
        users.save(new AppUser(SUB, "tester@example.com", "테스터", Instant.now()));
    }

    RequestPostProcessor session() {
        return oidcLogin()
            .idToken(t -> t.subject(SUB))
            .authorities(AuthorityUtils.createAuthorityList(Authorities.SESSION_AUTHORITIES));
    }

    String issueToken(String scopesJson) throws Exception {
        String body = mvc.perform(post("/api/v1/me/tokens").with(session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ci\",\"scopes\":" + scopesJson + ",\"expiresInDays\":30}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").value(startsWith("pst_")))
            .andExpect(jsonPath("$.info.active").value(true))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    @Test
    void apiWithoutAuthIs401WithBearerChallenge() throws Exception {
        mvc.perform(get("/api/v1/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate", containsString("Bearer")));
    }

    @Test
    void sessionUserSeesProfile() throws Exception {
        mvc.perform(get("/api/v1/me").with(session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("tester@example.com"))
            .andExpect(jsonPath("$.authType").value("session"));
    }

    @Test
    void issuedTokenAuthenticatesWithItsScopesOnly() throws Exception {
        String token = issueToken("[\"requests:read\"]");

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authType").value("token"))
            .andExpect(jsonPath("$.scopes[0]").value("requests:read"))
            .andExpect(jsonPath("$.scopes.length()").value(1));
    }

    @Test
    void tokenCannotManageTokens() throws Exception {
        String token = issueToken("[\"requests:read\",\"requests:write\"]");

        mvc.perform(get("/api/v1/me/tokens").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void revokedTokenIsRejected() throws Exception {
        String token = issueToken("[\"requests:read\"]");
        String list = mvc.perform(get("/api/v1/me/tokens").with(session()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(list, "$[0].id");
        assertThat(list).doesNotContain(token);

        mvc.perform(delete("/api/v1/me/tokens/" + id).with(session()).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer pst_not-a-real-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionWriteWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/api/v1/me/tokens").with(session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"scopes\":[\"requests:read\"]}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void unknownScopeIsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/me/tokens").with(session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"scopes\":[\"admin\"]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void googleLoginStartsAtGoogle() throws Exception {
        mvc.perform(get("/oauth2/authorization/google"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", startsWith("https://accounts.google.com/")))
            .andExpect(header().string("Location", containsString("redirect_uri=")));
    }

    @Test
    void unauthenticatedWebRequestGoesToLoginPage() throws Exception {
        mvc.perform(get("/some-protected-page"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }
}
