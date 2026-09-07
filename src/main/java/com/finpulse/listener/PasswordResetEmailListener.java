package com.finpulse.listener;


import com.finpulse.event.PasswordResetRequestedEvent;
import com.finpulse.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordResetEmailListener {


    private final EmailService emailService;

    @Async("notificationExecutor")
    @EventListener
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        try {
            emailService.sendPasswordResetLink(event.getEmail(), event.getRawToken());
        } catch (Exception e) {
            log.error("Password reset email failed to send. email={}", event.getEmail(), e);
        }
    }
}
