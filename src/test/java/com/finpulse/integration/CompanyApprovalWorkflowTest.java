package com.finpulse.integration;

import com.finpulse.AbstractIntegrationTest;
import com.finpulse.entity.*;
import com.finpulse.repository.AdminRepository;
import com.finpulse.repository.CompanyDecisionRepository;
import com.finpulse.repository.CompanyRepository;
import com.finpulse.repository.CompanyUserRepository;
import com.finpulse.security.JwtUtil;
import com.finpulse.testsupport.TestData;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class CompanyApprovalWorkflowTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private CompanyUserRepository companyUserRepository;
    @Autowired
    private CompanyDecisionRepository decisionRepository;
    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private JwtUtil jwtUtil;

    private String adminToken;
    @BeforeEach
    void setUp() {
        Admin admin = adminRepository.saveAndFlush(
                Admin.builder()
                        .email("admin" + System.nanoTime() + "@test.com")
                        .passwordHash("dummy-hash")
                        .role(Role.OWNER)
                        .mustChangePassword(false)
                        .createdAt(LocalDateTime.now())
                        .build());
        adminToken = jwtUtil.generateAccessToken(
                admin.getId(), SubjectType.ADMIN, null, "OWNER", false);
    }

    @Test
    void approve_pendingCompany_activatesCompanyCreatesOwnerAndDecision() throws Exception{
        Company company = companyRepository.saveAndFlush(
                TestData.company().companyStatus(CompanyStatus.PENDING).build());

        mockMvc.perform(post("/api/admin/companies/{id}/approve", company.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Company reloaded = companyRepository.findById(company.getId()).orElseThrow();
        assertEquals(CompanyStatus.ACTIVE, reloaded.getCompanyStatus());

        List<CompanyDecision> decisions = decisionRepository.findAll().stream()
                .filter(d -> d.getCompanyId().equals(company.getId()))
                .toList();
        assertEquals(1, decisions.size());
        assertEquals(DecisionType.APPROVED, decisions.get(0).getDecision());

        boolean ownerCreated = companyUserRepository.findByEmail(reloaded.getContactEmail())
                .filter(u -> u.getRole() == Role.OWNER)
                .isPresent();
        assertTrue(ownerCreated, "approve() should create an OWNER CompanyUser for the approved company");
    }

    @Test
    void reject_pendingCompany_marksRejectedAndCreatesNoUser() throws Exception {
        Company company = companyRepository.saveAndFlush(
                TestData.company().companyStatus(CompanyStatus.PENDING).build());

        String requestBody = """
                {"reason": "%s"}
                """.formatted("Failed compliance check");

        mockMvc.perform(post("/api/admin/companies/{id}/reject", company.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        Company reloaded = companyRepository.findById(company.getId()).orElseThrow();
        assertEquals(CompanyStatus.REJECTED, reloaded.getCompanyStatus());

        List<CompanyDecision> decisions = decisionRepository.findAll().stream()
                .filter(d -> d.getCompanyId().equals(company.getId()))
                .toList();
        assertEquals(1, decisions.size());
        assertEquals(DecisionType.REJECTED, decisions.get(0).getDecision());
        assertEquals("Failed compliance check", decisions.get(0).getReason());

        boolean anyUserCreated = companyUserRepository.findByEmail(reloaded.getContactEmail()).isPresent();
        assertFalse(anyUserCreated, "reject() must not create any CompanyUser");
    }
}
