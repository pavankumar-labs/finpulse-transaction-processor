package com.finpulse.repository;

import com.finpulse.entity.CompanyUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface CompanyUserRepository extends JpaRepository<CompanyUser, Long> {

    Optional<CompanyUser> findByEmail(String email);

    boolean existsByCompanyId(Long companyId);

    @Query("select u from CompanyUser u where u.companyId = :companyId and u.role = 'OWNER'")
    Optional<CompanyUser> findOwnerByCompanyId(Long companyId);
}
