package com.finpulse.repository;

import com.finpulse.entity.RejectedTransaction;
import com.finpulse.entity.RejectionStatus;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface RejectedTransactionRepository extends JpaRepository<RejectedTransaction,Long> {

    List<RejectedTransaction> findByCompanyIdAndStatus
            (Long companyId, RejectionStatus status);

    long countByCompanyIdAndFileProcessingIdAndStatus(Long companyId,String fileProcessingId,RejectionStatus status);

    @Query(value ="SELECT rt.* FROM rejected_transactions rt " +
            "JOIN uploaded_files uf ON rt.file_processing_id = uf.file_processing_id " +
            "WHERE rt.company_id = :companyId AND rt.status = :status " +
            "AND (:from IS NULL OR uf.uploaded_at >= :from) " +
            "AND (:to IS NULL OR uf.uploaded_at <= :to) " +
            "ORDER BY uf.uploaded_at DESC, rt.reason ASC, rt.rejected_at ASC",
            countQuery =
                    "SELECT COUNT(*) FROM rejected_transactions rt " +
                            "JOIN uploaded_files uf ON rt.file_processing_id = uf.file_processing_id " +
                            "WHERE rt.company_id = :companyId AND rt.status = :status " +
                            "AND (:from IS NULL OR uf.uploaded_at >= :from) " +
                            "AND (:to IS NULL OR uf.uploaded_at <= :to)"+
                            "AND (:fileProcessingId IS NULL OR rt.file_processing_id = :fileProcessingId)",
            nativeQuery = true)
    Page<RejectedTransaction> findRejectionsOrderedByFileRecency(
            @Param("companyId") Long companyId,
            @Param("status") String status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("fileProcessingId") String fileProcessingId,
            Pageable pageable);



}
