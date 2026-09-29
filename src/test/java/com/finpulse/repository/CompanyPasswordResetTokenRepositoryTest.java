package com.finpulse.repository;

import com.finpulse.AbstractIntegrationTest;
import com.finpulse.entity.Company;
import com.finpulse.entity.CompanyPasswordResetToken;
import com.finpulse.entity.CompanyUser;
import com.finpulse.testsupport.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class CompanyPasswordResetTokenRepositoryTest extends AbstractIntegrationTest {
    @Autowired
    private CompanyPasswordResetTokenRepository repository;
    @Autowired
    private TestEntityManager entityManager;

    private CompanyUser companyUser;

    @BeforeEach
    void setUp(){
        Company company = entityManager.persistAndFlush(TestData.company().build());
        companyUser = entityManager.persistAndFlush(TestData.companyUser(company.getId()).build());
    }

    private CompanyPasswordResetToken buildToken(String tokenHash, boolean used, LocalDateTime expiresAt) {
        return entityManager.persistAndFlush(
                CompanyPasswordResetToken.builder()
                        .companyUserId(companyUser.getId())
                        .tokenHash(tokenHash)
                        .used(used)
                        .expiresAt(expiresAt)
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }

    @Test
    void markUsedIfValid_validToken_marksUsedAndReturnsOne() {
        buildToken("hash-valid", false, LocalDateTime.now().plusHours(1));

        int rowsUpdated = repository.markUsedIfValid("hash-valid", LocalDateTime.now());
        entityManager.clear();

        assertEquals(1, rowsUpdated);
        CompanyPasswordResetToken reloaded = repository.findByTokenHash("hash-valid").orElseThrow();
        assertTrue(reloaded.isUsed());
    }

    @Test
    void markUsedIfValid_alreadyUsedToken_returnsZeroAndStaysUsed() {
        buildToken("hash-already-used", true, LocalDateTime.now().plusHours(1));

        int rowsUpdated = repository.markUsedIfValid("hash-already-used", LocalDateTime.now());
        entityManager.clear();

        assertEquals(0, rowsUpdated);
        CompanyPasswordResetToken reloaded = repository.findByTokenHash("hash-already-used").orElseThrow();
        assertTrue(reloaded.isUsed());
    }

    @Test
    void markUsedIfValid_expiredToken_returnsZeroAndStaysUnused() {
        buildToken("hash-expired", false, LocalDateTime.now().minusMinutes(1));

        int rowsUpdated = repository.markUsedIfValid("hash-expired", LocalDateTime.now());
        entityManager.clear();

        assertEquals(0, rowsUpdated);
        CompanyPasswordResetToken reloaded = repository.findByTokenHash("hash-expired").orElseThrow();
        assertFalse(reloaded.isUsed());
    }

    }
