package io.github.coldzero7362.pdfsign.mail;

/** 보낼 메일. html은 사용자 입력을 이미 이스케이프한 값이어야 한다. */
public record MailMessage(String to, String subject, String text, String html) {
}
