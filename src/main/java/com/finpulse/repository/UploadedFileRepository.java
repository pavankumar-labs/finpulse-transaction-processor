package com.finpulse.repository;

import com.finpulse.entity.UploadedFile;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UploadedFileRepository extends JpaRepository<UploadedFile,Long> {

    Optional<UploadedFile> findByCompanyIdAndFileHash(Long companyId, String fileHash);

    Optional<UploadedFile> findByFileProcessingId(String fileProcessingId);

    void deleteByFileProcessingId(String fileProcessingId);

    @Modifying
    @Transactional
    @Query("""
    update UploadedFile u
    set u.completedChunks = u.completedChunks + 1
    where u.fileProcessingId = :fileProcessingId
""")
    int incrementCompletedChunks(
            @Param("fileProcessingId") String fileProcessingId
    );



    @Modifying
    @Transactional
    @Query("""
        update UploadedFile u
        set u.completionEventPublished = true
        where u.fileProcessingId = :fileProcessingId
          and u.completedChunks >= u.totalChunks
          and u.completionEventPublished = false
        """)
    int claimCompletionEvent(
            @Param("fileProcessingId") String fileProcessingId
    );
}
