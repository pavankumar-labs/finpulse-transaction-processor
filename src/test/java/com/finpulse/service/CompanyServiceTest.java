package com.finpulse.service;

import com.finpulse.entity.*;
import com.finpulse.event.CompanyRejectedEvent;
import com.finpulse.exception.CompanyNotFoundException;
import com.finpulse.exception.InvalidCompanyStateException;
import com.finpulse.repository.CompanyDecisionRepository;
import com.finpulse.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CompanyServiceTest {

    @Mock private  CompanyRepository companyRepository;
    @Mock private  CompanyDecisionRepository decisionRepository;
    @Mock private   EmailService emailService;
    @Mock private  ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CompanyService companyService;

    @Test
    void reject_reasonIsNull_throwsInvalidCompanyStateException(){
        InvalidCompanyStateException thrown=assertThrows(InvalidCompanyStateException.class,
                ()->companyService.reject(1L, null, 4L));
        assertEquals("A rejection reason is required.",thrown.getMessage());
    }

    @Test
    void  reject_reasonIsBlank_throwsInvalidCompanyStateException(){
        InvalidCompanyStateException thrown=assertThrows(InvalidCompanyStateException.class,
                ()->companyService.reject(1L, " ", 4L));
        assertEquals("A rejection reason is required.",thrown.getMessage());
    }

    @Test
    void reject_companyNotFound_throwsCompanyNotFoundException(){
        when(companyRepository.findById(1L))
                .thenReturn(Optional.empty());
        CompanyNotFoundException thrown=assertThrows(CompanyNotFoundException.class,
                ()->companyService.reject(1L,"domain is not suitable",4L));
        assertEquals("No company found with id 1",thrown.getMessage());
    }

    @Test
    void reject_companyNotPending_throwsInvalidCompanyStateException(){
        Company fakeCompany= Company.builder()
                .id(1L)
                .companyStatus(CompanyStatus.REJECTED)
                .build();
        when(companyRepository.findById(1L))
                .thenReturn(Optional.of(fakeCompany));

        InvalidCompanyStateException thrown=assertThrows(InvalidCompanyStateException.class,
                ()->companyService.reject(1L,"domain is not suitable",4L));
        assertEquals("Company 1 is not in PENDING status. Current status: REJECTED",thrown.getMessage());

    }

    @Test
    void reject_success_rejectsCompanyAndPublishesEvent(){

        Company fakeCompany = Company.builder()
                .id(1L)
                .companyStatus(CompanyStatus.PENDING)
                .contactEmail("owner@acme.com")
                .companyName("Acme Inc")
                .companyCode("ACME01")
                .build();

        when(companyRepository.findById(1L))
                .thenReturn(Optional.of(fakeCompany));

        assertDoesNotThrow(() ->
                companyService.reject(1L, "Failed compliance check", 4L));

        assertEquals(CompanyStatus.REJECTED, fakeCompany.getCompanyStatus());
        verify(companyRepository).save(fakeCompany);

        ArgumentCaptor<CompanyDecision> decisionCaptor = ArgumentCaptor.forClass(CompanyDecision.class);
        verify(decisionRepository).save(decisionCaptor.capture());
        CompanyDecision savedDecision = decisionCaptor.getValue();
        assertEquals(1L, savedDecision.getCompanyId());
        assertEquals(4L, savedDecision.getAdminId());
        assertEquals("Failed compliance check", savedDecision.getReason());
        assertEquals(DecisionType.REJECTED, savedDecision.getDecision());

        ArgumentCaptor<CompanyRejectedEvent> eventCaptor = ArgumentCaptor.forClass(CompanyRejectedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        CompanyRejectedEvent event = eventCaptor.getValue();
        assertEquals("owner@acme.com", event.getContactEmail());
        assertEquals("Acme Inc", event.getCompanyName());
        assertEquals("ACME01", event.getCompanyCode());
    }
}
