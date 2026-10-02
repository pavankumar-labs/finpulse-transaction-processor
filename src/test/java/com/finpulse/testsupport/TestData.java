package com.finpulse.testsupport;

import com.finpulse.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

public final class TestData {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private TestData() {
    }

    public static Company.CompanyBuilder company(){
        int n = SEQUENCE.incrementAndGet();
        return Company.builder()
                .companyCode("COMP-" + n)
                .companyName("Test Company " + n)
                .contactEmail("company" + n + "@test.com")
                .companyStatus(CompanyStatus.PENDING);
    }

    public static UploadedFile.UploadedFileBuilder uploadedFile(Long companyId){
        int n = SEQUENCE.incrementAndGet();
        return UploadedFile.builder()
                .companyId(companyId)
                .fileProcessingId("file-processing-" + n)
                .fileHash("hash-" + n)
                .totalChunks(2)
                .completedChunks(0)
                .completionEventPublished(false)
                .uploadedAt(LocalDateTime.now());
    }

    public static CompanyUser.CompanyUserBuilder companyUser(Long companyId){
        int n = SEQUENCE.incrementAndGet();
        return CompanyUser.builder()
                .companyId(companyId)
                .email("user" + n + "@test.com")
                .passwordHash("dummy-hash-" + n)
                .role(Role.MEMBER)
                .mustChangePassword(false)
                .createdAt(LocalDateTime.now());
    }

    public static Transaction.TransactionBuilder transaction(Long companyId, String senderAccount, String receiverAccount){
        int n = SEQUENCE.incrementAndGet();
        return Transaction.builder()
                .transactionId("TXN-" + n)
                .senderAccount(senderAccount)
                .receiverAccount(receiverAccount)
                .amount(BigDecimal.valueOf(1000))
                .transactionTime(LocalDateTime.now())
                .fileName("test-file.csv")
                .fileProcessingId("proc-" + n)
                .companyId(companyId)
                .senderAccountType(AccountType.PERSONAL)
                .receiverAccountType(AccountType.PERSONAL);
    }

    public static FraudFinding.FraudFindingBuilder fraudFinding(Long companyId){
        int n = SEQUENCE.incrementAndGet();
        return FraudFinding.builder()
                .companyId(companyId)
                .fileProcessingId("file-processing-" + n)
                .accountNumber("ACC-" + n)
                .riskLevel(RiskLevel.HIGH)
                .triggeredRuleCodes("VELOCITY")
                .reason("Test fraud finding " + n)
                .status(FraudStatus.PENDING)
                .createdAt(LocalDateTime.now());
    }

    public static RejectedTransaction.RejectedTransactionBuilder rejectedTransaction(Long companyId, String transactionId){
        int n = SEQUENCE.incrementAndGet();
        return RejectedTransaction.builder()
                .companyId(companyId)
                .fileProcessingId("file-processing-" + n)
                .fileName("upload-" + n + ".csv")
                .rawLine(transactionId + ",ACC-OLD,ACC-OLD2,100.00,invalid-date,personal,business")
                .reason("INVALID_DATE")
                .transactionId(transactionId)
                .status(RejectionStatus.PENDING)
                .rejectedAt(LocalDateTime.now());
    }
}
