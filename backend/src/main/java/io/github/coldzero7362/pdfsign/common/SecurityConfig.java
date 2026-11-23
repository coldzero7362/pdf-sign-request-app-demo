package io.github.coldzero7362.pdfsign.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 골격 단계 보안 설정.
 * 기본은 모두 막고, 헬스 체크와 API 문서만 연다. 화면은 Next.js(web 컨테이너)가 맡는다.
 * Google 로그인(웹)과 PAT(API) 체인은 다음 단계에서 나눠 붙인다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/health", "/actuator/health").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
