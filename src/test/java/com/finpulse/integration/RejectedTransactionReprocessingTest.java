package com.finpulse.integration;

import com.finpulse.AbstractIntegrationTest;
import com.finpulse.entity.Company;
import com.finpulse.entity.CompanyStatus;
import com.finpulse.entity.RejectedTransaction;
import com.finpulse.entity.RejectionStatus;
import com.finpulse.entity.Transaction;
import com.finpulse.ingestion.FileChunk;
import com.finpulse.repository.CompanyRepository;
import com.finpulse.repository.RejectedTransactionRepository;
import com.finpulse.repository.TransactionRepository;
import com.finpulse.testsupport.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class RejectedTransactionReprocessingTest extends AbstractIntegrationTest {
    @Autowired
    private BlockingQueue<FileChunk> transactionQueue;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private RejectedTransactionRepository rejectedTransactionRepository;

    private Long companyId;
    private Long rejectionId;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.saveAndFlush(
                TestData.company()
                        .companyStatus(CompanyStatus.ACTIVE)
                        .build()
        );
        companyId = company.getId();
    }

    @AfterEach
    void tearDown() {
        if (companyId == null) {
            return;
        }
        await().atMost(15, TimeUnit.SECONDS)
                .ignoreExceptions()
                .untilAsserted(() -> {
                    transactionRepository.findAll().stream()
                            .filter(transaction -> companyId.equals(transaction.getCompanyId()))
                            .forEach(transactionRepository::delete);
                    transactionRepository.flush();

                    rejectedTransactionRepository.findAll().stream()
                            .filter(rejection -> companyId.equals(rejection.getCompanyId()))
                            .forEach(rejectedTransactionRepository::delete);
                    rejectedTransactionRepository.flush();

                    companyRepository.deleteById(companyId);
                    companyRepository.flush();
                });
    }

    @Test
    void reprocessingCorrectedTransaction_resolvesExistingPendingRejection()
            throws InterruptedException {
        String transactionId = "TXN-REPROCESS-1";
        RejectedTransaction existingRejection =
                rejectedTransactionRepository.saveAndFlush(
                        TestData.rejectedTransaction(companyId, transactionId)
                                .build()
                );
        rejectionId = existingRejection.getId();
        String correctedLine =
                transactionId
                        + ",ACC-NEW,ACC-NEW2,250.00,"
                        + "2026-09-15T10:30:00,"
                        + "personal,business";

        FileChunk correctedChunk = new FileChunk(
                "reprocess-file-id",
                "corrected-upload.csv",
                List.of(correctedLine),
                companyId
        );
        transactionQueue.put(correctedChunk);

        await()
                .atMost(30, TimeUnit.SECONDS)
                .ignoreExceptions()
                .untilAsserted(() -> {
                    Transaction savedTransaction =
                            transactionRepository.findAll()
                                    .stream()
                                    .filter(transaction ->
                                            transactionId.equals(
                                                    transaction.getTransactionId()))
                                    .findFirst()
                                    .orElseThrow();

                    assertEquals(
                            new BigDecimal("250.00"),
                            savedTransaction.getAmount()
                    );
                });

        await()
                .atMost(15, TimeUnit.SECONDS)
                .ignoreExceptions()
                .untilAsserted(() -> {
                    RejectedTransaction resolvedRejection =
                            rejectedTransactionRepository
                                    .findById(rejectionId)
                                    .orElseThrow();

                    assertEquals(
                            RejectionStatus.RESOLVED,
                            resolvedRejection.getStatus()
                    );

                    assertNotNull(resolvedRejection.getResolvedAt());
                });
    }
}