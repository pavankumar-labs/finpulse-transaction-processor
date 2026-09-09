package com.finpulse.listener;

import com.finpulse.event.CredentialIssuedEvent;
import com.finpulse.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class CredentialEmailListener {

    private final EmailService emailService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCredentialIssued(CredentialIssuedEvent event){

        try {
            emailService.sendCredentialEmail(event.getEmail(), event.getRawPassword());
        } catch (Exception e) {
            log.error("Credential email failed to send. email={}", event.getEmail(), e);
        }

    }

}


