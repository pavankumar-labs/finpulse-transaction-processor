package com.finpulse.listener;


import com.finpulse.event.CompanyApprovedEvent;
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
public class CompanyApprovalEmailListener {

    private final EmailService emailService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompanyApproved(CompanyApprovedEvent event){
        try {
            emailService.sendApprovalMail(
                    event.getContactEmail(), event.getCompanyName(), event.getCompanyCode(),
                    event.getRawApiKey(), event.getRawOwnerPassword());
        } catch (Exception e) {
            log.error("Approval email failed to send. contactEmail={}", event.getContactEmail(), e);
        }

    }
}
