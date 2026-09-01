package com.finpulse.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    public void sendRegistrationConfirmation
            (String toEmail,String companyName,String companyCode){
        try{
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setSubject("FinPulse — Application Received");
            message.setTo(toEmail);
            message.setText("Hi " + companyName + ",\n\n"
                    + "We've received your application to connect with FinPulse. "
                    + "Our team will review it shortly.\n\n"
                    + "You can check your application status anytime using your company code (" + companyCode + ") "
                    + "at: GET /api/companies/status/" + companyCode + "\n\n"
                    + "— FinPulse Team");
            mailSender.send(message);
            log.info("Registration confirmation email sent to {}", toEmail);
        } catch (MailException e) {
            log.error("Failed to send registration confirmation to {}. Company code: {}", toEmail, companyCode, e);
        }
    }

    public void sendApprovalMail
            (String toEmail,String companyName,String companyCode,String rawApiKey){
        try{
            SimpleMailMessage message=new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject("FinPulse — You're Approved!");
            message.setText("Hi " + companyName + ",\n\n"
                    + "Your application has been approved. You're now connected to FinPulse.\n\n"
                    + "Company Code: " + companyCode + "\n"
                    + "API Key: " + rawApiKey + "\n\n"
                    + "Keep this key secure — it will not be shown again. "
                    + "Include it as the 'X-API-Key' header on every request to our transaction ingestion API.\n\n"
                    + "— FinPulse Team"
            );
            mailSender.send(message);
            log.info("Approval email sent to {}", toEmail);
        } catch (MailException e) {
            log.error("Failed to send approval email to {}. Company code: {}", toEmail, companyCode, e);
        }

    }

    public void sendRejectionEmail(String toEmail,String companyName){
        try{
            SimpleMailMessage message=new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject("FinPulse — Application Update");
            message.setText("Hi " + companyName + ",\n\n"
                    + "After review, we're unable to approve your application to connect with FinPulse at this time.\n\n"
                    + "If you'd like more information, please reach out to our support team.\n\n"
                    + "— FinPulse Team"
            );
            mailSender.send(message);
            log.info("Rejection email sent to {}", toEmail);
        } catch (MailException e) {
            log.error("Failed to send rejection email to {}", toEmail, e);
        }
    }
}
