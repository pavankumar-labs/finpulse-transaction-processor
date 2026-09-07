package com.finpulse.repository;

import com.finpulse.entity.FraudFinding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudFindingRepository extends JpaRepository<FraudFinding,Long> {

    long countByCompanyIdAndFileProcessingId(Long companyId, String fileProcessingId);
}
