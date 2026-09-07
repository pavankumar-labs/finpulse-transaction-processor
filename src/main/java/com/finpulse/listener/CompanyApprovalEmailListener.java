package com.finpulse.listener;


import com.finpulse.event.CompanyApprovedEvent;
import com.finpulse.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyApprovalEmailListener {

    private EmailService emailService;

    @Async("notificationExecutor")
    @EventListener
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
