package io.github.coldzero7362.pdfsign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PdfSignApplication {

    public static void main(String[] args) {
        SpringApplication.run(PdfSignApplication.class, args);
    }
}
