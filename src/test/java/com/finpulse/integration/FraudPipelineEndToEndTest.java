package com.finpulse.integration;

import com.finpulse.AbstractIntegrationTest;
import com.finpulse.entity.Company;
import com.finpulse.entity.CompanyStatus;
import com.finpulse.entity.Transaction;
import com.finpulse.repository.CompanyRepository;
import com.finpulse.repository.FraudFindingRepository;
import com.finpulse.repository.NotificationRepository;
import com.finpulse.repository.TransactionRepository;
import com.finpulse.repository.UploadedFileRepository;
import com.finpulse.security.ApiKeyGenerator;
import com.finpulse.testsupport.TestData;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class FraudPipelineEndToEndTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private FraudFindingRepository fraudFindingRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    private Long companyId;
    private String rawApiKey;

    @BeforeEach
    void setUp() {
        String rawKey = ApiKeyGenerator.generateRawKey();

        Company company = companyRepository.saveAndFlush(
                TestData.company()
                        .companyStatus(CompanyStatus.ACTIVE)
                        .apiHashCode(ApiKeyGenerator.hash(rawKey))
                        .build()
        );
        companyId = company.getId();
        this.rawApiKey = rawKey;
    }

    @AfterEach
    void tearDown() {
        if (companyId == null) {
            return;
        }
        await().atMost(20, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    notificationRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                            .forEach(notificationRepository::delete);

                    fraudFindingRepository.findAll().stream()
                            .filter(f -> companyId.equals(f.getCompanyId()))
                            .forEach(fraudFindingRepository::delete);
                    fraudFindingRepository.flush();

                    transactionRepository.findAll().stream()
                            .filter(t -> companyId.equals(t.getCompanyId()))
                            .forEach(transactionRepository::delete);
                    transactionRepository.flush();

                    uploadedFileRepository.findAll().stream()
                            .filter(u -> companyId.equals(u.getCompanyId()))
                            .forEach(uploadedFileRepository::delete);
                    uploadedFileRepository.flush();

                    companyRepository.deleteById(companyId);
                    companyRepository.flush();
                });
    }

    @Test
    void uploadedVelocityBurst_flowsThroughQueueWorkersFraudAnalysisAndNotification() throws Exception {
        String csvContent = """
                transaction_id,sender_account,receiver_account,amount,transaction_time,sender_account_type,receiver_account_type
                TXN-V1,VELOCITY-SENDER,ACC-R1,100.00,2026-09-15T10:28:00,personal,personal
                TXN-V2,VELOCITY-SENDER,ACC-R2,100.00,2026-09-15T10:29:00,personal,personal
                TXN-V3,VELOCITY-SENDER,ACC-R3,100.00,2026-09-15T10:30:00,personal,personal
                TXN-V4,VELOCITY-SENDER,ACC-R4,100.00,2026-09-15T10:31:00,personal,personal
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "velocity-burst.csv", "text/csv", csvContent.getBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/ledger/upload")
                        .file(file)
                        .header("X-API-Key", rawApiKey))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = uploadResult.getResponse().getContentAsString();
        String fileProcessingId = JsonPath.read(responseBody, "$.data[0]");

        await().atMost(30, TimeUnit.SECONDS)
                .ignoreExceptions()
                .untilAsserted(() -> {
                    List<Transaction> inserted = transactionRepository.findByFileProcessingId(fileProcessingId);
                    assertEquals(4, inserted.size());
                });

        await().atMost(30, TimeUnit.SECONDS)
                .ignoreExceptions()
                .untilAsserted(() -> {
                    long findingCount = fraudFindingRepository
                            .countByCompanyIdAndFileProcessingId(companyId, fileProcessingId);
                    assertEquals(1, findingCount);
                });

        await().atMost(15, TimeUnit.SECONDS)
                .ignoreExceptions()
                .untilAsserted(() -> {
                    boolean hasFlaggedNotification = notificationRepository
                            .findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                            .anyMatch(n -> n.getMessage().contains("1 account(s) flagged"));
                    assertTrue(hasFlaggedNotification);
                });
    }
}