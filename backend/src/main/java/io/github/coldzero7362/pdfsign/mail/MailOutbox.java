package io.github.coldzero7362.pdfsign.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 메일 발송 창구. 트랜잭션 안에서 부르면 커밋된 뒤에 보낸다.
 * (DB 저장이 롤백됐는데 메일만 나가는 일을 막는다)
 */
@Component
public class MailOutbox {

    private static final Logger log = LoggerFactory.getLogger(MailOutbox.class);

    private final MailSender sender;
    private final TaskExecutor executor;

    public MailOutbox(MailSender sender, @Qualifier("mailExecutor") TaskExecutor executor) {
        this.sender = sender;
        this.executor = executor;
    }

    public void send(MailMessage message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(message);
                }
            });
        } else {
            dispatch(message);
        }
    }

    private void dispatch(MailMessage message) {
        executor.execute(() -> {
            try {
                sender.send(message);
            } catch (RuntimeException e) {
                log.error("메일 발송 실패 to={} subject={}", message.to(), message.subject(), e);
            }
        });
    }
}
