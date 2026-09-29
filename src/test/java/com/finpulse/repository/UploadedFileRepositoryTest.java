package com.finpulse.repository;

import com.finpulse.AbstractIntegrationTest;
import com.finpulse.entity.Company;
import com.finpulse.entity.UploadedFile;
import com.finpulse.testsupport.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class UploadedFileRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private UploadedFileRepository repository;
    @Autowired
    private TestEntityManager entityManager;
    private Company company;

    @BeforeEach
    void setUp() {
        company = entityManager.persistAndFlush(TestData.company().build());
    }

    @Test
    void claimCompletionEvent_returnsOneAndMarksPublished_whenAllChunksCompleted(){
        UploadedFile file=entityManager.persistAndFlush(
                TestData.uploadedFile(company.getId())
                        .totalChunks(2)
                        .completedChunks(2)
                        .build()
        );

        int rowsUpdated = repository.claimCompletionEvent(file.getFileProcessingId());
        entityManager.clear();

        assertEquals(1, rowsUpdated);
        UploadedFile reloaded = repository.findByFileProcessingId(file.getFileProcessingId())
                .orElseThrow();
        assertTrue(reloaded.isCompletionEventPublished());
    }

    @Test
    void claimCompletionEvent_returnsZeroOnSecondCall_soEventIsPublishedOnlyOnce(){
        UploadedFile file = entityManager.persistAndFlush(
                TestData.uploadedFile(company.getId())
                        .totalChunks(2)
                        .completedChunks(2)
                        .build());

        int firstClaim = repository.claimCompletionEvent(file.getFileProcessingId());
        int secondClaim = repository.claimCompletionEvent(file.getFileProcessingId());

        assertEquals(1, firstClaim);
        assertEquals(0, secondClaim);
    }

    @Test
    void incrementCompletedChunks_singleCall_updatesStoredValue() {
        Company company = entityManager.persistAndFlush(TestData.company().build());
        UploadedFile file = entityManager.persistAndFlush(
                TestData.uploadedFile(company.getId())
                        .completedChunks(0)
                        .build()
        );
        int rowsUpdated = repository.incrementCompletedChunks(file.getFileProcessingId());

        assertEquals(1, rowsUpdated);

        entityManager.clear();
        UploadedFile reloaded = repository.findByFileProcessingId(file.getFileProcessingId())
                .orElseThrow();

        assertEquals(1, reloaded.getCompletedChunks());
    }

    @Test
    void incrementCompletedChunks_calledTwice_accumulatesToTwo() {
        Company company = entityManager.persistAndFlush(TestData.company().build());
        UploadedFile file = entityManager.persistAndFlush(
                TestData.uploadedFile(company.getId())
                        .completedChunks(0)
                        .build()
        );
        int firstCall = repository.incrementCompletedChunks(file.getFileProcessingId());
        int secondCall = repository.incrementCompletedChunks(file.getFileProcessingId());

        assertEquals(1, firstCall);
        assertEquals(1, secondCall);

        entityManager.clear();
        UploadedFile reloaded = repository.findByFileProcessingId(file.getFileProcessingId())
                .orElseThrow();

        assertEquals(2, reloaded.getCompletedChunks());
    }
}
