package com.finpulse.listener;

import com.finpulse.event.CompanyApprovedEvent;
import com.finpulse.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CompanyApprovalEmailListenerTest {

    @Mock private EmailService emailService;

    @InjectMocks
    private CompanyApprovalEmailListener companyApprovalEmailListener;

    @Test
    void onCompanyApproved_emailServiceThrows_catchesAndDoesNotPropagate(){

        CompanyApprovedEvent fakeEvent = new CompanyApprovedEvent(
                "owner@acme.com", "Acme Inc", "ACME01", "rawKey123", "rawPassword123");

        doThrow(new RuntimeException("Brevo API down"))
                .when(emailService)
                .sendApprovalMail(anyString(), anyString(), anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> companyApprovalEmailListener.onCompanyApproved(fakeEvent));

        verify(emailService).sendApprovalMail(
                "owner@acme.com", "Acme Inc", "ACME01", "rawKey123", "rawPassword123");

    }

    @Test
    void onCompanyApproved_success_sendsApprovalEmail(){
        CompanyApprovedEvent fakeEvent = new CompanyApprovedEvent(
                "owner@acme.com", "Acme Inc", "ACME01", "rawKey123", "rawPassword123");
        doNothing()
                .when(emailService)
                .sendApprovalMail(anyString(), anyString(), anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> companyApprovalEmailListener.onCompanyApproved(fakeEvent));

        verify(emailService).sendApprovalMail(
                "owner@acme.com", "Acme Inc", "ACME01", "rawKey123", "rawPassword123");

    }

}
