package io.github.coldzero7362.pdfsign.mail;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 메일 설정이 없을 때 쓰는 발송기. 실제로 보내지 않고 로그에 받는 사람과 제목만 남긴다.
 * 테스트에서 보낸 메일을 확인할 수 있게 최근 메일을 메모리에 들고 있다.
 */
public class LoggingMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);
    private static final int KEEP = 50;

    private final List<MailMessage> sent = new ArrayList<>();

    @Override
    public synchronized void send(MailMessage m) {
        log.info("메일 설정이 없어 발송을 건너뜀: to={} subject={}", m.to(), m.subject());
        sent.add(m);
        if (sent.size() > KEEP) {
            sent.removeFirst();
        }
    }

    public synchronized List<MailMessage> sent() {
        return List.copyOf(sent);
    }

    public synchronized void clear() {
        sent.clear();
    }
}
