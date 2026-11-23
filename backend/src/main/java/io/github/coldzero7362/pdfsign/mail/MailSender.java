package io.github.coldzero7362.pdfsign.mail;

/** 실제 발송 수단. SMTP 또는 로그(개발·테스트). */
public interface MailSender {

    void send(MailMessage message);
}
