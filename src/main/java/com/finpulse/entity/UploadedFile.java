package com.finpulse.entity;


import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "uploaded_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "file_processing_id", nullable = false)
    private String fileProcessingId;


    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "total_chunks")
    private Integer totalChunks;

    @Column(name = "completed_chunks", nullable = false)
    private Integer completedChunks;

    @Column(name = "completion_event_published", nullable = false)
    private boolean completionEventPublished;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
}
