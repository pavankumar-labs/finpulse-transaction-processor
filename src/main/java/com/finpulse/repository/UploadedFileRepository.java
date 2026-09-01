package com.finpulse.repository;

import com.finpulse.entity.UploadedFile;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UploadedFileRepository extends JpaRepository<UploadedFile,Long> {

    Optional<UploadedFile> findByCompanyIdAndFileHash(Long companyId, String fileHash);

    Optional<UploadedFile> findByFileProcessingId(String fileProcessingId);

    @Modifying
    @Query("update UploadedFile u set u.completedChunks=u.completedChunks+1 where u.fileProcessingId= :fileProcessingId")
    int incrementCompletedChunks(@Param("fileProcessingId") String fileProcessingId);



}
