package io.github.coldzero7362.pdfsign.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

@Configuration
public class MailConfig {

    @Bean
    MailSender appMailSender(JavaMailSender javaMailSender,
                          @Value("${app.mail.from:}") String from,
                          @Value("${app.mail.from-name}") String fromName) {
        return StringUtils.hasText(from)
            ? new SmtpMailSender(javaMailSender, from, fromName)
            : new LoggingMailSender();
    }

    /** 메일은 요청 처리와 분리해 보낸다. 테스트에서는 바로 보낸다 (app.mail.async=false). */
    @Bean
    TaskExecutor mailExecutor(@Value("${app.mail.async:true}") boolean async) {
        if (!async) {
            return new SyncTaskExecutor();
        }
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("mail-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(4);
        return executor;
    }
}
