package com.finpulse.service;


import com.finpulse.service.email.EmailTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final BrevoEmailGateway brevoEmailGateway;
    private final EmailTemplates emailTemplates;

    @Async
    public void sendRegistrationConfirmation(String email, String companyName, String companyCode) {
        String subject = "FinPulse — Application Received";
        String html = EmailTemplates.registrationConfirmation(companyName, companyCode);
        send(email, companyName, subject, html, "registration confirmation", companyCode);
    }

    @Async
    public void sendApprovalNotice(String email, String companyName, String companyCode, String rawApiKey) {
        String subject = "FinPulse — You're Approved!";
        String html = EmailTemplates.approvalNotice(companyName, companyCode, rawApiKey);
        send(email, companyName, subject, html, "approval notice", companyCode);
    }

    @Async
    public void sendRejectionNotice(String email, String companyName, String companyCode) {
        String subject = "FinPulse — Application Update";
        String html = EmailTemplates.rejectionNotice(companyName);
        send(email, companyName, subject, html, "rejection notice", companyCode);
    }

    private void send(String email, String toName, String subject, String html, String emailType, String companyCode) {
        BrevoEmailGateway.EmailSendResult result = brevoEmailGateway.sendEmail(email, toName, subject, html);

        if (!result.success()) {
            log.error("{} email failed for company {}: {}", emailType, companyCode, result.errorMessage());
        } else {
            log.info("{} email sent for company {} (messageId={})", emailType, companyCode, result.messageId());
        }
    }



}
