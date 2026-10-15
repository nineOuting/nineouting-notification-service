package com.nineouting.backend.notification.service;

import com.nineouting.backend.notification.config.MailDeliveryProperties;
import com.nineouting.backend.notification.domain.OutgoingMail;
import com.nineouting.backend.notification.repository.OutgoingMailRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쌓인 메일(outgoing_mails)을 SMTP로 보낸다 (REQ-009, 011, 100).
 * 꺼낸 메일은 잠가 두므로 서비스를 여러 개 띄워도 같은 메일을 두 번 보내지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailDeliveryJob {

    private final OutgoingMailRepository repository;
    private final MailComposer composer;
    private final JavaMailSender mailSender;
    private final MailDeliveryProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${nineouting.mail.send-interval:5s}")
    @Transactional
    public void deliver() {
        if (!properties.enabled()) {
            return;
        }
        List<OutgoingMail> due = repository.findDue(now(), properties.batchSize());
        for (OutgoingMail mail : due) {
            try {
                mailSender.send(composer.compose(mail));
                mail.markSent(now());
            } catch (MailException | IllegalArgumentException e) {
                mail.markFailed(e.getMessage(), now(), properties.maxAttempts());
                if (OutgoingMail.STATUS_FAILED.equals(mail.getStatus())) {
                    log.error("메일 {} 발송을 {}번 실패해 멈춥니다: {}", mail.getId(), mail.getAttempts(), e.getMessage());
                } else {
                    log.warn("메일 {} 발송 실패 ({}번째), {}에 다시 보냅니다: {}", mail.getId(), mail.getAttempts(),
                            mail.getNextAttemptAt(), e.getMessage());
                }
            }
        }
    }

    /** 보낸 메일 기록을 지운다. 실패(FAILED)는 원인 확인을 위해 남긴다 */
    @Scheduled(cron = "${nineouting.mail.cleanup-cron:0 30 5 * * *}")
    @Transactional
    public void deleteSent() {
        int deleted = repository.deleteSentBefore(now().minus(properties.sentRetention()));
        if (deleted > 0) {
            log.info("보낸 메일 기록 {}건을 지웠습니다", deleted);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
