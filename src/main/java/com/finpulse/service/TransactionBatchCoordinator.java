package com.finpulse.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionBatchCoordinator {

    private static final int CHUNK_SIZE = 4000;
    private static final int COPY_BUFFER_SIZE = 8192;

    private final MeterRegistry registry;
    private final BlockingQueue<FileChunk> transactionQueue;
    private final UploadedFileRepository uploadedFileRepository;

    private Counter filesReceivedCounter;

    @PostConstruct
    public void initializeMetrics() {
        filesReceivedCounter = Counter.builder("finpulse.files.received.total")
                .description("Total files received for ingestion")
                .register(registry);
    }

    public String streamFileContents(
            String fileName,
            InputStream fileInputStream,
            Long companyId
    ) throws IOException, InterruptedException {

        Path temporaryFile = Files.createTempFile(
                "finpulse-upload-",
                ".tmp"
        );

        try {

            storeUploadTemporarily(fileInputStream, temporaryFile);

            String fileHash = computeFileHash(temporaryFile);

            if (uploadedFileRepository
                    .findByCompanyIdAndFileHash(companyId, fileHash)
                    .isPresent()) {

                log.warn(
                        "Duplicate file submission rejected. companyId={}, fileName={}",
                        companyId,
                        fileName
                );

                throw new InvalidFileException(
                        "This file has already been submitted. Duplicate uploads are not processed."
                );
            }

            filesReceivedCounter.increment();

            String fileProcessingId = UUID.randomUUID().toString();

            log.info(
                    "File ingestion started. fileProcessingId={}, fileName={}, companyId={}",
                    fileProcessingId,
                    fileName,
                    companyId
            );
            int chunkCount = countChunks(temporaryFile);
            if (chunkCount == 0) {
                throw new IllegalArgumentException("No transactions were uploaded");
            }

            uploadedFileRepository.save(
                    UploadedFile.builder()
                            .companyId(companyId)
                            .fileProcessingId(fileProcessingId)
                            .fileHash(fileHash)
                            .uploadedAt(LocalDateTime.now())
                            .completedChunks(0)
                            .totalChunks(chunkCount)
                            .build()
            );

            try {

                queueChunks(
                        temporaryFile,
                        fileName,
                        fileProcessingId,
                        companyId
                );

            } catch (Exception e){
                log.error(
                        "Failed to queue file chunks. Removing uploaded file record. " +
                                "fileProcessingId={}, fileName={}, companyId={}",
                        fileProcessingId,
                        fileName,
                        companyId,
                        e
                );

                uploadedFileRepository.deleteByFileProcessingId(
                        fileProcessingId
                );

                throw e;
            }
            log.info(
                    "File ingestion queued. fileProcessingId={}, fileName={}, companyId={}, totalChunks={}",
                    fileProcessingId,
                    fileName,
                    companyId,
                    chunkCount
            );

            return fileProcessingId;

        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    private void storeUploadTemporarily(
            InputStream inputStream,
            Path temporaryFile
    ) throws IOException {

        try (
                InputStream source = inputStream;
                OutputStream target = Files.newOutputStream(
                        temporaryFile,
                        StandardOpenOption.WRITE
                )
        ) {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];

            int bytesRead;

            while ((bytesRead = source.read(buffer)) != -1) {
                target.write(buffer, 0, bytesRead);
            }
        }
    }

    private String computeFileHash(Path temporaryFile) throws IOException {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (InputStream inputStream = Files.newInputStream(temporaryFile)) {

                byte[] buffer = new byte[COPY_BUFFER_SIZE];

                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

            byte[] hashBytes = digest.digest();

            StringBuilder hex = new StringBuilder(hashBytes.length * 2);

            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 algorithm not available",
                    e
            );
        }
    }

    private int countChunks(Path temporaryFile) throws IOException {

        int chunkCount = 0;
        int currentChunkSize = 0;

        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                Files.newInputStream(temporaryFile),
                                StandardCharsets.UTF_8
                        )
                )
        ) {

            reader.readLine();

            String line;

            while ((line = reader.readLine()) != null) {

                currentChunkSize++;

                if (currentChunkSize == CHUNK_SIZE) {
                    chunkCount++;
                    currentChunkSize = 0;
                }
            }

            if (currentChunkSize > 0) {
                chunkCount++;
            }
        }

        return chunkCount;
    }

    private void queueChunks(
            Path temporaryFile,
            String fileName,
            String fileProcessingId,
            Long companyId
    ) throws IOException, InterruptedException {

        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                Files.newInputStream(temporaryFile),
                                StandardCharsets.UTF_8
                        )
                )
        ) {

            reader.readLine();

            String line;

            List<String> currentChunk = new ArrayList<>(CHUNK_SIZE);

            while ((line = reader.readLine()) != null) {

                currentChunk.add(line);

                if (currentChunk.size() == CHUNK_SIZE) {

                    transactionQueue.put(
                            new FileChunk(
                                    fileProcessingId,
                                    fileName,
                                    currentChunk,
                                    companyId
                            )
                    );

                    currentChunk = new ArrayList<>(CHUNK_SIZE);
                }
            }

            if (!currentChunk.isEmpty()) {

                transactionQueue.put(
                        new FileChunk(
                                fileProcessingId,
                                fileName,
                                currentChunk,
                                companyId
                        )
                );
            }
        }
    }

    private void deleteTemporaryFile(Path temporaryFile) {

        try {
            Files.deleteIfExists(temporaryFile);

            log.debug(
                    "Temporary upload file deleted. path={}",
                    temporaryFile
            );

        } catch (IOException e) {
            log.error(
                    "Failed to delete temporary upload file. path={}",
                    temporaryFile,
                    e
            );
        }
    }
}