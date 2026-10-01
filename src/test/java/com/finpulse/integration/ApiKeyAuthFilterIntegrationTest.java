package com.finpulse.integration;

import com.finpulse.AbstractIntegrationTest;
import com.finpulse.entity.CompanyStatus;
import com.finpulse.repository.CompanyRepository;
import com.finpulse.security.ApiKeyGenerator;
import com.finpulse.testsupport.TestData;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
public class ApiKeyAuthFilterIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CompanyRepository companyRepository;

    private static final String UPLOAD_URL = "/api/v1/ledger/upload";

    @Test
    void upload_withMissingApiKeyHeader_isUnauthorized() throws Exception {
        mockMvc.perform(post(UPLOAD_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Missing X-API-Key header"));
    }

    @Test
    void upload_withUnknownApiKey_isUnauthorized() throws Exception {
        mockMvc.perform(post(UPLOAD_URL).header("X-API-Key", "fp_live_does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Invalid API key"));
    }

    @Test
    void upload_withValidKeyForInactiveCompany_isForbidden() throws Exception {
        String rawKey = ApiKeyGenerator.generateRawKey();
        companyRepository.saveAndFlush(TestData.company()
                .companyStatus(CompanyStatus.PENDING)
                .apiHashCode(ApiKeyGenerator.hash(rawKey))
                .build());

        mockMvc.perform(post(UPLOAD_URL).header("X-API-Key", rawKey))
                .andExpect(status().isForbidden())
                .andExpect(status().reason("Company is not active"));
    }

    @Test
    void upload_withValidKeyForActiveCompany_passesFilterAndReachesController() throws Exception {
        String rawKey = ApiKeyGenerator.generateRawKey();
        companyRepository.saveAndFlush(TestData.company()
                .companyStatus(CompanyStatus.ACTIVE)
                .apiHashCode(ApiKeyGenerator.hash(rawKey))
                .build());

        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.csv", "text/csv",
                "transaction_id,sender_account,receiver_account,amount,transaction_time,sender_account_type,receiver_account_type\n"
                        .getBytes());

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(emptyFile)
                        .header("X-API-Key", rawKey))
                .andExpect(status().isBadRequest());
    }
}
