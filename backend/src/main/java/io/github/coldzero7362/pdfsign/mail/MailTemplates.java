package io.github.coldzero7362.pdfsign.mail;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * 메일 문구. 제목·메시지·이름은 사용자가 입력한 값이라 HTML에 넣기 전에 반드시 이스케이프한다.
 */
@Component
public class MailTemplates {

    private static final DateTimeFormatter KST = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.of("Asia/Seoul"));

    private final String baseUrl;

    public MailTemplates(@Value("${app.base-url}") String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public String signLink(String rawToken) {
        return baseUrl + "/sign/" + rawToken;
    }

    public String dashboardLink(String requestPublicId) {
        return baseUrl + "/requests/" + requestPublicId;
    }

    public MailMessage invitation(String to, String recipientName, String senderName, String senderEmail,
                                  String title, String message, Instant expiresAt, String link) {
        String subject = "[서명 요청] " + oneLine(title);
        StringBuilder text = new StringBuilder()
            .append(recipientName).append("님, ").append(senderName).append("(").append(senderEmail)
            .append(")님이 문서 작성과 서명을 요청했습니다.\n\n")
            .append("문서: ").append(title).append("\n");
        if (message != null) {
            text.append("메시지: ").append(message).append("\n");
        }
        if (expiresAt != null) {
            text.append("기한: ").append(KST.format(expiresAt)).append(" (한국 시각)\n");
        }
        text.append("\n아래 링크에서 작성해 주세요. 링크를 열면 이 메일 주소로 인증번호를 보내 드립니다.\n")
            .append(link).append("\n\n이 링크는 본인만 사용할 수 있으니 다른 사람에게 전달하지 마세요.\n");

        String html = layout(
            p(esc(recipientName) + "님, <b>" + esc(senderName) + "</b>(" + esc(senderEmail)
                + ")님이 문서 작성과 서명을 요청했습니다.")
            + table(row("문서", esc(title))
                + (message != null ? row("메시지", esc(message).replace("\n", "<br>")) : "")
                + (expiresAt != null ? row("기한", KST.format(expiresAt) + " (한국 시각)") : ""))
            + button(link, "문서 열기")
            + small("링크를 열면 이 메일 주소로 인증번호를 보내 드립니다. 이 링크는 본인만 사용할 수 있으니 다른 사람에게 전달하지 마세요."));
        return new MailMessage(to, subject, text.toString(), html);
    }

    public MailMessage otp(String to, String code, String title) {
        String subject = "[인증번호] " + code + " - " + oneLine(title);
        String text = "인증번호: " + code + "\n\n10분 동안 유효합니다. 요청한 적이 없다면 이 메일을 무시하세요.\n";
        String html = layout(
            p("문서 <b>" + esc(title) + "</b> 작성을 위한 인증번호입니다.")
            + "<p style=\"font-size:28px;font-weight:700;letter-spacing:6px;margin:16px 0\">" + esc(code) + "</p>"
            + small("10분 동안 유효합니다. 요청한 적이 없다면 이 메일을 무시하세요."));
        return new MailMessage(to, subject, text, html);
    }

    public MailMessage completedToRecipient(String to, String name, String title, String link) {
        String subject = "[완료] " + oneLine(title);
        String text = name + "님, 모든 참여자가 작성을 마쳐 문서가 완료되었습니다.\n\n"
            + "아래 링크에서 완료된 PDF를 내려받을 수 있습니다 (인증번호 확인 후).\n" + link + "\n";
        String html = layout(
            p(esc(name) + "님, 모든 참여자가 작성을 마쳐 문서 <b>" + esc(title) + "</b>가 완료되었습니다.")
            + button(link, "완료된 문서 받기")
            + small("링크를 열고 인증번호를 확인하면 내려받을 수 있습니다."));
        return new MailMessage(to, subject, text, html);
    }

    public MailMessage completedToOwner(String to, String title, String link) {
        String subject = "[완료] " + oneLine(title);
        String text = "요청한 문서 '" + title + "'의 작성이 모두 끝났습니다.\n" + link + "\n";
        String html = layout(p("요청한 문서 <b>" + esc(title) + "</b>의 작성이 모두 끝났습니다.")
            + button(link, "요청 보기"));
        return new MailMessage(to, subject, text, html);
    }

    public MailMessage declinedToOwner(String to, String title, String who, String reason, String link) {
        String subject = "[거절] " + oneLine(title);
        String text = who + "님이 '" + title + "' 요청을 거절했습니다.\n"
            + (reason != null ? "사유: " + reason + "\n" : "") + link + "\n";
        String html = layout(p("<b>" + esc(who) + "</b>님이 <b>" + esc(title) + "</b> 요청을 거절했습니다.")
            + (reason != null ? table(row("사유", esc(reason).replace("\n", "<br>"))) : "")
            + button(link, "요청 보기"));
        return new MailMessage(to, subject, text, html);
    }

    public MailMessage canceledToRecipient(String to, String name, String title) {
        String subject = "[취소] " + oneLine(title);
        String text = name + "님, '" + title + "' 요청이 요청자에 의해 취소되었습니다. 더 이상 작성하지 않아도 됩니다.\n";
        String html = layout(p(esc(name) + "님, <b>" + esc(title)
            + "</b> 요청이 요청자에 의해 취소되었습니다. 더 이상 작성하지 않아도 됩니다."));
        return new MailMessage(to, subject, text, html);
    }

    // ---------- HTML 조각 ----------

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s);
    }

    /** 메일 제목 줄바꿈 주입 방지 */
    private static String oneLine(String s) {
        String v = s.replaceAll("[\\r\\n]+", " ").strip();
        return v.length() > 120 ? v.substring(0, 120) + "…" : v;
    }

    private static String layout(String body) {
        return "<!doctype html><html><body style=\"margin:0;padding:24px;background:#f7f7f8;"
            + "font-family:-apple-system,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;color:#1c1c1e\">"
            + "<div style=\"max-width:520px;margin:0 auto;background:#fff;border:1px solid #e3e3e6;border-radius:8px;padding:24px\">"
            + "<p style=\"margin:0 0 16px;font-weight:700\">PDF 서명 요청</p>" + body + "</div></body></html>";
    }

    private static String p(String html) {
        return "<p style=\"margin:0 0 12px;line-height:1.6\">" + html + "</p>";
    }

    private static String table(String rows) {
        return "<table style=\"border-collapse:collapse;margin:0 0 16px;font-size:14px\">" + rows + "</table>";
    }

    private static String row(String k, String v) {
        return "<tr><td style=\"padding:4px 12px 4px 0;color:#6b6b72;vertical-align:top;white-space:nowrap\">" + k
            + "</td><td style=\"padding:4px 0\">" + v + "</td></tr>";
    }

    private static String button(String href, String label) {
        return "<p style=\"margin:20px 0\"><a href=\"" + esc(href) + "\" style=\"display:inline-block;background:#2f5bea;"
            + "color:#fff;text-decoration:none;padding:10px 18px;border-radius:6px;font-weight:600\">" + label + "</a></p>";
    }

    private static String small(String html) {
        return "<p style=\"margin:16px 0 0;font-size:12px;color:#6b6b72;line-height:1.5\">" + html + "</p>";
    }
}
