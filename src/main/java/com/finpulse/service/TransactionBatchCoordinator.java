package com.finpulse.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

import com.finpulse.entity.UploadedFile;
import com.finpulse.exception.InvalidFileException;
import com.finpulse.ingestion.FileChunk;
import com.finpulse.repository.UploadedFileRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionBatchCoordinator{

    private final MeterRegistry registry;
    private Counter filesReceivedCounter;
    private final BlockingQueue<FileChunk> transactionQueue;
    private static final int CHUNK_SIZE = 4000;
    private final UploadedFileRepository uploadedFileRepository;

     @PostConstruct
     public void initializeMetrics(){
         filesReceivedCounter=Counter.builder("finpulse.files.received.total")
                 .description("Total files received for ingestion")
                 .register(registry);
     }

    public String streamFileContents(String fileName,InputStream fileInputStream,Long companyId)
                                            throws IOException,InterruptedException{

         byte[] fileBytes=fileInputStream.readAllBytes();
         String fileHash=computeFileHash(fileBytes);
        if (uploadedFileRepository.findByCompanyIdAndFileHash(companyId, fileHash).isPresent()) {
            log.warn("Duplicate file submission rejected. companyId={}, fileName={}", companyId, fileName);
            throw new InvalidFileException("This file has already been submitted. Duplicate uploads are not processed.");
        }

         filesReceivedCounter.increment();
        String fileProcessingId =
                UUID.randomUUID().toString();
        int chunkCount = 0;

        log.info(
                "File ingestion started. fileProcessingId={}, fileName={}, companyId={}",
                fileProcessingId,
                fileName,
                companyId
        );
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(fileInputStream))){

            reader.readLine();
            String line;
            List<String> currentChunk=new ArrayList<>(CHUNK_SIZE);

            while ((line=reader.readLine())!=null) {
                currentChunk.add(line);
                if(currentChunk.size()==CHUNK_SIZE){
                    transactionQueue.put(new FileChunk(fileProcessingId,fileName,currentChunk,companyId));
                    currentChunk=new ArrayList<>(CHUNK_SIZE);
                    chunkCount++;
                }   
            }

            if (!currentChunk.isEmpty()) {
                transactionQueue.put(new FileChunk(fileProcessingId,fileName, currentChunk,companyId));
                chunkCount++;
            }
        }

        uploadedFileRepository.save(UploadedFile.builder()
                .companyId(companyId)
                .fileProcessingId(fileProcessingId)
                .fileHash(fileHash)
                .uploadedAt(LocalDateTime.now())
                .completedChunks(0)
                .totalChunks(chunkCount)
                .build());

        log.info("File ingestion started. fileProcessingId={}, fileName={}, companyId={}, totalChunks={}",
                fileProcessingId, fileName, companyId, chunkCount);

        return fileProcessingId;
}
    private String computeFileHash(byte[] fileBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(fileBytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }


}