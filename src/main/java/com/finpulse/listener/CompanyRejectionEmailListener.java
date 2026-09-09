package com.finpulse.listener;

import com.finpulse.event.CompanyRejectedEvent;
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
public class CompanyRejectionEmailListener {

    private final EmailService emailService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompanyRejected(CompanyRejectedEvent event) {

        try {
            emailService.sendRejectionNotice(
                    event.getContactEmail(),
                    event.getCompanyName(),
                    event.getCompanyCode()
            );
        } catch (Exception e) {
            log.error(
                    "Failed to send company rejection email for company {}",
                    event.getCompanyCode(),
                    e
            );
        }


    }
}
