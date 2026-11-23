package io.github.coldzero7362.pdfsign.auth;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

/**
 * 보안 체인 세 개.
 * 1) /api/**: 브라우저 세션(Google 로그인) 또는 Bearer API 토큰. 미인증은 401.
 * 2) /sign/api/**: 수신자(계정 없음). 링크 토큰 + OTP 세션은 SignAccessService가 확인한다.
 *    로그인 페이지로 보내지 않고 401/403으로 답한다.
 * 3) 나머지: Google 로그인 흐름, 로그아웃, API 문서. 미인증은 화면의 /login으로 보낸다.
 *
 * CSRF: 세션(쿠키)으로 들어오는 변경 요청은 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보내야 한다.
 * Bearer 토큰 요청은 쿠키를 쓰지 않으므로 resource server 설정이 CSRF 검사에서 자동으로 뺀다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http, ApiTokenIntrospector introspector) throws Exception {
        http
            .securityMatcher("/api/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/health").permitAll()
                .requestMatchers("/api/v1/me/tokens", "/api/v1/me/tokens/**").hasAuthority(Authorities.SESSION)
                // 템플릿: 조회는 templates:read, 만들기·수정·삭제는 웹 세션만 (지침 1)
                .requestMatchers(HttpMethod.GET, "/api/v1/templates", "/api/v1/templates/**")
                    .hasAuthority("SCOPE_" + Authorities.SCOPE_TEMPLATES_READ)
                .requestMatchers("/api/v1/templates", "/api/v1/templates/**").hasAuthority(Authorities.SESSION)
                // 문서와 요청: 조회는 requests:read, 변경은 requests:write (세션은 둘 다 가짐)
                .requestMatchers(HttpMethod.GET, "/api/v1/documents/**", "/api/v1/requests/**")
                    .hasAuthority("SCOPE_" + Authorities.SCOPE_REQUESTS_READ)
                .requestMatchers("/api/v1/documents/**", "/api/v1/requests/**")
                    .hasAuthority("SCOPE_" + Authorities.SCOPE_REQUESTS_WRITE)
                .anyRequest().hasAuthority(Authorities.USER))
            .oauth2ResourceServer(rs -> rs.opaqueToken(ot -> ot.introspector(introspector)))
            .csrf(csrf -> csrf.spa());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain recipientChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/sign/api/**")
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.spa())
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler(new AccessDeniedHandlerImpl()));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain webChain(HttpSecurity http, AppOidcUserService oidcUserService) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                // 오류 응답(sendError → /error 내부 전달)이 로그인 리디렉션으로 바뀌지 않게 한다
                .requestMatchers("/error").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated())
            .oauth2Login(login -> login
                .loginPage("/login")
                .userInfoEndpoint(ui -> ui.oidcUserService(oidcUserService))
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error"))
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
            .csrf(csrf -> csrf.spa());
        return http.build();
    }
}
