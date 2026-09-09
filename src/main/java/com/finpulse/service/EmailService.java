package com.finpulse.service;


import com.finpulse.service.email.EmailTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final BrevoEmailGateway brevoEmailGateway;


    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Async
    public void sendRegistrationConfirmation(String email, String companyName, String companyCode) {
        String subject = "FinPulse — Application Received";
        String html = EmailTemplates.registrationConfirmation(companyName, companyCode);
        send(email, companyName, subject, html, "registration confirmation", companyCode);
    }

    public void sendApprovalMail(String email, String companyName, String companyCode, String rawApiKey, String rawOwnerPassword) {

        String html = EmailTemplates.approvalNotice(
                companyName,
                email,
                companyCode,
                rawApiKey,
                rawOwnerPassword
        );
        send(email, companyName, "FinPulse — You're Approved!", html, "approval notice", companyCode);
    }



    public void sendRejectionNotice(String email, String companyName, String companyCode) {
        String subject = "FinPulse — Application Update";
        String html = EmailTemplates.rejectionNotice(companyName);
        send(email, companyName, subject, html, "rejection notice", companyCode);
    }


    public void sendCredentialEmail(String email, String rawPassword) {
        String html = EmailTemplates.credentialIssued(email, rawPassword);
        send(email, email, "FinPulse — Your Account Is Ready", html, "credential issued", "n/a");
    }


    public void sendPasswordResetLink(String email, String rawToken) {
        String resetLink = frontendBaseUrl + "/reset-password?token=" + rawToken;
        String html = EmailTemplates.passwordResetLink(resetLink);
        send(email, email, "FinPulse — Reset Your Password", html, "password reset link", "n/a");
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
