package com.finpulse.repository;

import com.finpulse.entity.FraudFinding;
import com.finpulse.entity.FraudStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudFindingRepository extends JpaRepository<FraudFinding,Long> {

    long countByCompanyIdAndFileProcessingId(Long companyId, String fileProcessingId);

    Page<FraudFinding> findByCompanyIdAndStatus(Long companyId, FraudStatus status, Pageable pageable);

    Page<FraudFinding> findByCompanyIdAndFileProcessingIdAndStatus(
            Long companyId, String fileProcessingId, FraudStatus status, Pageable pageable);
}
