package com.finpulse.integration;

import com.finpulse.AbstractIntegrationTest;
import com.finpulse.entity.*;
import com.finpulse.repository.CompanyRepository;
import com.finpulse.repository.CompanyUserRepository;
import com.finpulse.security.JwtUtil;
import com.finpulse.testsupport.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class TeammateCreationTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private CompanyUserRepository companyUserRepository;
    @Autowired
    private JwtUtil jwtUtil;

    private Long companyId;
    private String ownerToken;
    private String memberToken;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.saveAndFlush(
                TestData.company().companyStatus(CompanyStatus.ACTIVE).build());
        companyId = company.getId();
        CompanyUser owner = companyUserRepository.saveAndFlush(
                TestData.companyUser(companyId).role(Role.OWNER).build());
        CompanyUser member = companyUserRepository.saveAndFlush(
                TestData.companyUser(companyId).role(Role.MEMBER).build());

        ownerToken = jwtUtil.generateAccessToken(
                owner.getId(), SubjectType.COMPANY_USER, companyId, "OWNER", false);
        memberToken = jwtUtil.generateAccessToken(
                member.getId(), SubjectType.COMPANY_USER, companyId, "MEMBER", false);
    }

    @Test
    void createTeammate_calledByOwner_createsNewMemberInDatabase() throws Exception {
        String newTeammateEmail = "new-hire@test.com";
        String requestBody = """
                {"email": "%s"}
                """.formatted(newTeammateEmail);

        mockMvc.perform(post("/api/company/users")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        boolean teammateExists = companyUserRepository.findByEmail(newTeammateEmail).isPresent();
        assertTrue(teammateExists, "createTeammate should have persisted a new CompanyUser row");
    }

    @Test
    void createTeammate_calledByNonOwnerMember_isForbiddenAndCreatesNothing() throws Exception {
        String attemptedEmail = "should-not-exist@test.com";
        String requestBody = """
                {"email": "%s"}
                """.formatted(attemptedEmail);

        mockMvc.perform(post("/api/company/users")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());

        boolean teammateWasCreatedAnyway = companyUserRepository.findByEmail(attemptedEmail).isPresent();
        assertTrue(!teammateWasCreatedAnyway, "a rejected request must not create a row");
    }
}