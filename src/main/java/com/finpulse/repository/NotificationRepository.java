package com.finpulse.repository;

import com.finpulse.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification,Long> {

    List<Notification> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Long countByCompanyIdAndViewedFalse(Long companyId);

    void deleteByCompanyId(Long CompanyId);
}
