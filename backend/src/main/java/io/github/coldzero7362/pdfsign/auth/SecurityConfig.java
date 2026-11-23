package io.github.coldzero7362.pdfsign.auth;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

/**
 * 보안 체인 두 개.
 * 1) /api/**: 브라우저 세션(Google 로그인) 또는 Bearer API 토큰. 미인증은 401.
 * 2) 나머지: Google 로그인 흐름, 로그아웃, API 문서. 미인증은 화면의 /login으로 보낸다.
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
                .anyRequest().hasAuthority(Authorities.USER))
            .oauth2ResourceServer(rs -> rs.opaqueToken(ot -> ot.introspector(introspector)))
            .csrf(csrf -> csrf.spa());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webChain(HttpSecurity http, AppOidcUserService oidcUserService) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
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
