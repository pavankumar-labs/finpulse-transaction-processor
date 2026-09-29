package com.finpulse.repository;

import com.finpulse.AbstractIntegrationTest;
import com.finpulse.entity.RejectedTransaction;
import com.finpulse.entity.RejectionStatus;
import com.finpulse.testsupport.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class RejectedTransactionRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private RejectedTransactionRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private Long companyAId;
    private Long companyBId;

    @BeforeEach
    void setUp() {
        companyAId = entityManager.persistAndFlush(TestData.company().build()).getId();
        companyBId = entityManager.persistAndFlush(TestData.company().build()).getId();
    }

    private RejectedTransaction buildRejection(Long companyId, String fileProcessingId, RejectionStatus status) {
        return entityManager.persistAndFlush(
                RejectedTransaction.builder()
                        .companyId(companyId)
                        .fileProcessingId(fileProcessingId)
                        .fileName("test-file.csv")
                        .rawLine("bad,row,data")
                        .reason("INVALID_AMOUNT")
                        .status(status)
                        .rejectedAt(LocalDateTime.now())
                        .build()
        );
    }

    private void persistUploadedFile(Long companyId, String fileProcessingId) {
        entityManager.persistAndFlush(
                TestData.uploadedFile(companyId)
                        .fileProcessingId(fileProcessingId)
                        .build()
        );
    }

    @Test
    void findByCompanyIdAndStatus_matchingCompanyAndStatus_isReturned() {
        buildRejection(companyAId, "proc-1", RejectionStatus.PENDING);

        List<RejectedTransaction> result =
                repository.findByCompanyIdAndStatus(companyAId, RejectionStatus.PENDING);

        assertEquals(1, result.size());
    }

    @Test
    void findByCompanyIdAndStatus_matchingCompanyWrongStatus_isExcluded() {
        buildRejection(companyAId, "proc-1", RejectionStatus.RESOLVED);

        List<RejectedTransaction> result =
                repository.findByCompanyIdAndStatus(companyAId, RejectionStatus.PENDING);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByCompanyIdAndStatus_matchingStatusWrongCompany_isExcluded() {
        buildRejection(companyBId, "proc-1", RejectionStatus.PENDING);

        List<RejectedTransaction> result =
                repository.findByCompanyIdAndStatus(companyAId, RejectionStatus.PENDING);

        assertTrue(result.isEmpty());
    }

    @Test
    void countByCompanyIdAndFileProcessingIdAndStatus_matchingAllThree_returnsCorrectCount() {
        buildRejection(companyAId, "proc-1", RejectionStatus.PENDING);
        buildRejection(companyAId, "proc-1", RejectionStatus.PENDING);

        long count = repository.countByCompanyIdAndFileProcessingIdAndStatus(
                companyAId, "proc-1", RejectionStatus.PENDING);

        assertEquals(2, count);
    }

    @Test
    void countByCompanyIdAndFileProcessingIdAndStatus_differentFile_isExcluded() {
        buildRejection(companyAId, "proc-1", RejectionStatus.PENDING);

        long count = repository.countByCompanyIdAndFileProcessingIdAndStatus(
                companyAId, "proc-2", RejectionStatus.PENDING);

        assertEquals(0, count);
    }

    @Test
    void findRejectionsOrderedByFileRecency_noFilters_returnsAllMatchingRows() {
        persistUploadedFile(companyAId, "proc-1");
        persistUploadedFile(companyAId, "proc-2");

        buildRejection(companyAId, "proc-1", RejectionStatus.PENDING);
        buildRejection(companyAId, "proc-2", RejectionStatus.PENDING);
        Page<RejectedTransaction> result = repository.findRejectionsOrderedByFileRecency(
                companyAId, "PENDING", null, null, null, PageRequest.of(0, 10));

        assertEquals(2, result.getTotalElements());
    }

    @Test
    void findRejectionsOrderedByFileRecency_fileProcessingIdFilter_returnsOnlyThatFile() {
        persistUploadedFile(companyAId, "proc-1");
        persistUploadedFile(companyAId, "proc-2");

        buildRejection(companyAId, "proc-1", RejectionStatus.PENDING);
        buildRejection(companyAId, "proc-2", RejectionStatus.PENDING);
        Page<RejectedTransaction> result = repository.findRejectionsOrderedByFileRecency(
                companyAId, "PENDING", null, null, "proc-1", PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals("proc-1", result.getContent().get(0).getFileProcessingId());
    }

    @Test
    void findRejectionsOrderedByFileRecency_differentCompany_isExcluded() {
        persistUploadedFile(companyBId, "proc-1");
        buildRejection(companyBId, "proc-1", RejectionStatus.PENDING);
        Page<RejectedTransaction> result = repository.findRejectionsOrderedByFileRecency(
                companyAId, "PENDING", null, null, null, PageRequest.of(0, 10));

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void findRejectionsOrderedByFileRecency_ordersByMostRecentFileFirst() {
        entityManager.persistAndFlush(
                TestData.uploadedFile(companyAId)
                        .fileProcessingId("proc-old")
                        .uploadedAt(LocalDateTime.now().minusDays(5))
                        .build()
        );
        entityManager.persistAndFlush(
                TestData.uploadedFile(companyAId)
                        .fileProcessingId("proc-new")
                        .uploadedAt(LocalDateTime.now())
                        .build()
        );
        buildRejection(companyAId, "proc-old", RejectionStatus.PENDING);
        buildRejection(companyAId, "proc-new", RejectionStatus.PENDING);

        Page<RejectedTransaction> result = repository.findRejectionsOrderedByFileRecency(
                companyAId, "PENDING", null, null, null, PageRequest.of(0, 10));

        assertEquals("proc-new", result.getContent().get(0).getFileProcessingId());
        assertEquals("proc-old", result.getContent().get(1).getFileProcessingId());
    }
}