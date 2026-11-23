package io.github.coldzero7362.pdfsign.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI에서 API 토큰(Bearer)으로 호출해 볼 수 있게 한다. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
            .info(new Info().title("PDF Sign Request API").version("v1"))
            .components(new Components().addSecuritySchemes("apiToken",
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                    .description("설정 화면에서 발급한 API 토큰 (pst_...)")))
            .addSecurityItem(new SecurityRequirement().addList("apiToken"));
    }
}
