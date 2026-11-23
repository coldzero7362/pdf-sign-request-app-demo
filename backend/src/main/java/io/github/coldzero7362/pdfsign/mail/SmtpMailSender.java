package io.github.coldzero7362.pdfsign.mail;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** Gmail SMTP 발송. */
public class SmtpMailSender implements MailSender {

    private final JavaMailSender sender;
    private final String from;
    private final String fromName;

    public SmtpMailSender(JavaMailSender sender, String from, String fromName) {
        this.sender = sender;
        this.from = from;
        this.fromName = fromName;
    }

    @Override
    public void send(MailMessage m) {
        try {
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(from, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(m.to());
            helper.setSubject(m.subject());
            helper.setText(m.text(), m.html());
            sender.send(mime);
        } catch (Exception e) {
            throw new IllegalStateException("메일 발송 실패: " + m.to(), e);
        }
    }
}
