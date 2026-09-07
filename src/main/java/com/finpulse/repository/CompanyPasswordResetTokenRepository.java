package com.finpulse.repository;

import com.finpulse.entity.CompanyPasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CompanyPasswordResetTokenRepository extends JpaRepository<CompanyPasswordResetToken,Long> {

    Optional<CompanyPasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update CompanyPasswordResetToken t set t.used = true " +
            "where t.tokenHash = :tokenHash and t.used = false and t.expiresAt > :now")
    int markUsedIfValid(String tokenHash, LocalDateTime now);

}
