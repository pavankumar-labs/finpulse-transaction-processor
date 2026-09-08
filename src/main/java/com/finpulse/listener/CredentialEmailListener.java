package com.finpulse.listener;

import com.finpulse.event.CredentialIssuedEvent;
import com.finpulse.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CredentialEmailListener {

    private final EmailService emailService;

    @Async("notificationExecutor")
    @EventListener
    public void onCredentialIssued(CredentialIssuedEvent event){

        try {
            emailService.sendCredentialEmail(event.getEmail(), event.getRawPassword());
        } catch (Exception e) {
            log.error("Credential email failed to send. email={}", event.getEmail(), e);
        }

    }

}


