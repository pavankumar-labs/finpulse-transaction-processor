package com.finpulse.repository;

import com.finpulse.AbstractIntegrationTest;
import com.finpulse.entity.Transaction;
import com.finpulse.testsupport.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class TransactionRepositoryTest extends AbstractIntegrationTest {
    @Autowired
    private TransactionRepository repository;
    @Autowired
    private TestEntityManager entityManager;

    private Long companyAId;
    private Long companyBId;

    @BeforeEach
    void setUp() {
        companyAId = entityManager.persistAndFlush(TestData.company().build()).getId();
        companyBId = entityManager.persistAndFlush(TestData.company().build()).getId();
    }

    @Test
    void findSenderHistory_transactionWithinWindow_isReturned(){
        entityManager.persistAndFlush(
                TestData.transaction(companyAId, "ACC-SENDER", "ACC-OTHER")
                        .transactionTime(LocalDateTime.now().minusDays(5))
                        .build()
        );

        List<Transaction> result = repository.findSenderHistory(
                companyAId, Set.of("ACC-SENDER"), LocalDateTime.now().minusDays(30)
        );

        assertEquals(1, result.size());
        assertEquals("ACC-SENDER", result.get(0).getSenderAccount());
    }

    @Test
    void findSenderHistory_transactionBeforeWindow_isExcluded(){
        entityManager.persistAndFlush(
                TestData.transaction(companyAId, "ACC-SENDER", "ACC-OTHER")
                        .transactionTime(LocalDateTime.now().minusDays(40))
                        .build()
        );

        List<Transaction> result = repository.findSenderHistory(
                companyAId, Set.of("ACC-SENDER"), LocalDateTime.now().minusDays(30)
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void findSenderHistory_sameAccountDifferentCompany_isExcluded(){
        entityManager.persistAndFlush(
                TestData.transaction(companyBId, "ACC-SENDER", "ACC-OTHER")
                        .transactionTime(LocalDateTime.now().minusDays(5))
                        .build()
        );

        List<Transaction> result = repository.findSenderHistory(
                companyAId, Set.of("ACC-SENDER"), LocalDateTime.now().minusDays(30)
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void findSenderHistory_accountNotInRequestedSet_isExcluded(){
        entityManager.persistAndFlush(
                TestData.transaction(companyAId, "ACC-SENDER", "ACC-OTHER")
                        .transactionTime(LocalDateTime.now().minusDays(5))
                        .build()
        );

        List<Transaction> result = repository.findSenderHistory(
                companyAId, Set.of("ACC-DIFFERENT-SENDER"), LocalDateTime.now().minusDays(30)
        );

        assertTrue(result.isEmpty());
    }


    @Test
    void findReceiverHistory_transactionWithinWindow_isReturned() {
        entityManager.persistAndFlush(
                TestData.transaction(companyAId, "ACC-OTHER", "ACC-RECEIVER")
                        .transactionTime(LocalDateTime.now().minusDays(5))
                        .build()
        );

        List<Transaction> result = repository.findReceiverHistory(
                companyAId, Set.of("ACC-RECEIVER"), LocalDateTime.now().minusDays(30)
        );

        assertEquals(1, result.size());
        assertEquals("ACC-RECEIVER", result.get(0).getReceiverAccount());
    }

    @Test
    void findReceiverHistory_transactionBeforeWindow_isExcluded() {
        entityManager.persistAndFlush(
                TestData.transaction(companyAId, "ACC-OTHER", "ACC-RECEIVER")
                        .transactionTime(LocalDateTime.now().minusDays(40))
                        .build()
        );

        List<Transaction> result = repository.findReceiverHistory(
                companyAId, Set.of("ACC-RECEIVER"), LocalDateTime.now().minusDays(30)
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void findReceiverHistory_sameAccountDifferentCompany_isExcluded() {
        entityManager.persistAndFlush(
                TestData.transaction(companyBId, "ACC-OTHER", "ACC-RECEIVER")
                        .transactionTime(LocalDateTime.now().minusDays(5))
                        .build()
        );

        List<Transaction> result = repository.findReceiverHistory(
                companyAId, Set.of("ACC-RECEIVER"), LocalDateTime.now().minusDays(30)
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void findReceiverHistory_accountNotInRequestedSet_isExcluded() {
        entityManager.persistAndFlush(
                TestData.transaction(companyAId, "ACC-OTHER", "ACC-RECEIVER")
                        .transactionTime(LocalDateTime.now().minusDays(5))
                        .build()
        );

        List<Transaction> result = repository.findReceiverHistory(
                companyAId, Set.of("ACC-DIFFERENT-RECEIVER"), LocalDateTime.now().minusDays(30)
        );

        assertTrue(result.isEmpty());
    }
}
