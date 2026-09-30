package com.finpulse.integration;

import com.finpulse.AbstractIntegrationTest;
import com.finpulse.entity.*;
import com.finpulse.repository.CompanyRepository;
import com.finpulse.repository.CompanyUserRepository;
import com.finpulse.repository.FraudFindingRepository;
import com.finpulse.security.JwtUtil;
import com.finpulse.testsupport.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class CrossTenantIsolationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyUserRepository companyUserRepository;
    @Autowired private FraudFindingRepository fraudFindingRepository;
    @Autowired private JwtUtil jwtUtil;

    private FraudFinding findingOfCompanyA;
    private String tokenForCompanyBOwner;

    @BeforeEach
    void setUp() {
        Company companyA = companyRepository.saveAndFlush(
                TestData.company().companyStatus(CompanyStatus.ACTIVE).build());
        Company companyB = companyRepository.saveAndFlush(
                TestData.company().companyStatus(CompanyStatus.ACTIVE).build());

        CompanyUser ownerB = companyUserRepository.saveAndFlush(
                TestData.companyUser(companyB.getId()).role(Role.OWNER).build());

        findingOfCompanyA = fraudFindingRepository.saveAndFlush(
                TestData.fraudFinding(companyA.getId()).build());

        tokenForCompanyBOwner = jwtUtil.generateAccessToken(
                ownerB.getId(), SubjectType.COMPANY_USER, companyB.getId(), "OWNER", false);
    }

    @Test
    void resolveFinding_calledByDifferentCompanyOwner_returnsNotFoundAndLeavesFindingUnchanged() throws Exception {
        mockMvc.perform(patch("/api/v1/fraud-findings/{id}/resolve", findingOfCompanyA.getId())
                        .header("Authorization", "Bearer " + tokenForCompanyBOwner))
                .andExpect(status().isNotFound());

        FraudFinding reloaded = fraudFindingRepository.findById(findingOfCompanyA.getId()).orElseThrow();
        assertEquals(FraudStatus.PENDING, reloaded.getStatus());
        assertNull(reloaded.getResolvedAt());
    }

    @Test
    void getFindings_scopedToAnotherCompanysFile_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/v1/fraud-findings")
                        .header("Authorization", "Bearer " + tokenForCompanyBOwner)
                        .param("fileProcessingId", findingOfCompanyA.getFileProcessingId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }
}