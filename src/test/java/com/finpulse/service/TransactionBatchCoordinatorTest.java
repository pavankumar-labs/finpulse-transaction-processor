package com.finpulse.service;

import com.finpulse.entity.UploadedFile;
import com.finpulse.exception.InvalidFileException;
import com.finpulse.ingestion.FileChunk;
import com.finpulse.repository.UploadedFileRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionBatchCoordinatorTest {

    @Mock private BlockingQueue<FileChunk> transactionQueue;
    @Mock private UploadedFileRepository uploadedFileRepository;
    private SimpleMeterRegistry meterRegistry;
    private TransactionBatchCoordinator coordinator;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        coordinator = new TransactionBatchCoordinator(
                meterRegistry,
                transactionQueue,
                uploadedFileRepository
        );
        coordinator.initializeMetrics();
    }

    private ByteArrayInputStream inputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldAcceptValidFilePersistMetadataAndQueueChunk()
            throws IOException, InterruptedException {
        String csv = """
                transaction_id,sender_account,receiver_account,amount,transaction_time,sender_account_type,receiver_account_type
                TXN001,ACC001,ACC002,1000.50,2026-09-15T10:30:00,personal,business
                TXN002,ACC003,ACC004,2500.00,2026-09-15T11:30:00,business,personal
                """;
        Long companyId = 1L;
        when(uploadedFileRepository.findByCompanyIdAndFileHash(eq(companyId), anyString()))
                .thenReturn(Optional.empty());
        String processingId = coordinator.streamFileContents("transactions.csv", inputStream(csv), companyId);

        assertNotNull(processingId);
        ArgumentCaptor<UploadedFile> uploadedFileCaptor = ArgumentCaptor.forClass(UploadedFile.class);
        verify(uploadedFileRepository).save(uploadedFileCaptor.capture());
        UploadedFile savedFile = uploadedFileCaptor.getValue();

        assertEquals(companyId, savedFile.getCompanyId());
        assertEquals(processingId, savedFile.getFileProcessingId());
        assertEquals(1, savedFile.getTotalChunks());
        assertEquals(0, savedFile.getCompletedChunks());
        assertNotNull(savedFile.getFileHash());
        assertNotNull(savedFile.getUploadedAt());

        ArgumentCaptor<FileChunk> chunkCaptor = ArgumentCaptor.forClass(FileChunk.class);
        verify(transactionQueue).put(chunkCaptor.capture());
        FileChunk chunk = chunkCaptor.getValue();

        assertEquals(processingId, chunk.getFileProcessingId());
        assertEquals("transactions.csv", chunk.getFileName());
        assertEquals(companyId, chunk.getCompanyId());
        assertEquals(2, chunk.getLines().size());

        verify(uploadedFileRepository, never()).deleteByFileProcessingId(anyString());
        assertEquals(1.0, meterRegistry.get("finpulse.files.received.total").counter().count());
    }

    @Test
    void shouldRejectDuplicateFileAndNotSaveOrQueue()
            throws IOException, InterruptedException {

        String csv = """
                transaction_id,sender_account,receiver_account,amount,transaction_time,sender_account_type,receiver_account_type
                TXN001,ACC001,ACC002,1000.00,2026-09-15T10:30:00,personal,business
                """;
        Long companyId = 1L;
        when(uploadedFileRepository.findByCompanyIdAndFileHash(eq(companyId), anyString()))
                .thenReturn(Optional.of(UploadedFile.builder().build()));
        InvalidFileException exception = assertThrows(InvalidFileException.class,
                () -> coordinator.streamFileContents("transactions.csv", inputStream(csv), companyId));

        assertEquals("This file has already been submitted. Duplicate uploads are not processed.",
                exception.getMessage());
        verify(uploadedFileRepository, never()).save(any(UploadedFile.class));
        verify(uploadedFileRepository, never()).deleteByFileProcessingId(anyString());
        verify(transactionQueue, never()).put(any(FileChunk.class));
        assertEquals(0.0, meterRegistry.get("finpulse.files.received.total").counter().count());
    }

    @Test
    void shouldRejectEmptyFile()
            throws IOException, InterruptedException {
        String csv = """
                transaction_id,sender_account,receiver_account,amount,transaction_time,sender_account_type,receiver_account_type
                """;
        Long companyId = 1L;
        when(uploadedFileRepository.findByCompanyIdAndFileHash(eq(companyId), anyString()))
                .thenReturn(Optional.empty());
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> coordinator.streamFileContents("empty.csv", inputStream(csv), companyId));

        assertEquals("No transactions were uploaded", exception.getMessage());
        verify(uploadedFileRepository, never()).save(any(UploadedFile.class));
        verify(transactionQueue, never()).put(any(FileChunk.class));
        assertEquals(1.0, meterRegistry.get("finpulse.files.received.total").counter().count());
    }

    @Test
    void shouldDeleteUploadedFileRecordWhenQueueingFails()
            throws IOException, InterruptedException {
        String csv = """
                transaction_id,sender_account,receiver_account,amount,transaction_time,sender_account_type,receiver_account_type
                TXN001,ACC001,ACC002,1000.00,2026-09-15T10:30:00,personal,business
                """;
        Long companyId = 1L;
        when(uploadedFileRepository.findByCompanyIdAndFileHash(eq(companyId), anyString()))
                .thenReturn(Optional.empty());
        doThrow(new IllegalStateException("Queue unavailable"))
                .when(transactionQueue).put(any(FileChunk.class));
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> coordinator.streamFileContents("transactions.csv", inputStream(csv), companyId));

        assertEquals("Queue unavailable", exception.getMessage());

        ArgumentCaptor<UploadedFile> uploadedFileCaptor = ArgumentCaptor.forClass(UploadedFile.class);
        verify(uploadedFileRepository).save(uploadedFileCaptor.capture());
        String processingId = uploadedFileCaptor.getValue().getFileProcessingId();
        verify(uploadedFileRepository).deleteByFileProcessingId(processingId);
        verify(transactionQueue).put(any(FileChunk.class));
    }

    @Test
    void shouldCreateMultipleChunksWhenFileExceedsChunkSize()
            throws IOException, InterruptedException {
        StringBuilder csv = new StringBuilder();
        csv.append("transaction_id,sender_account,receiver_account,amount,transaction_time,sender_account_type,receiver_account_type\n");
        for (int i = 1; i <= 4001; i++) {
            csv.append("TXN").append(i).append(",ACC001,ACC002,100.00,2026-09-15T10:30:00,personal,business\n");
        }
        Long companyId = 1L;
        when(uploadedFileRepository.findByCompanyIdAndFileHash(eq(companyId), anyString()))
                .thenReturn(Optional.empty());
        String processingId = coordinator.streamFileContents("large.csv", inputStream(csv.toString()), companyId);

        assertNotNull(processingId);

        ArgumentCaptor<UploadedFile> uploadedFileCaptor = ArgumentCaptor.forClass(UploadedFile.class);
        verify(uploadedFileRepository).save(uploadedFileCaptor.capture());
        assertEquals(2, uploadedFileCaptor.getValue().getTotalChunks());

        ArgumentCaptor<FileChunk> chunkCaptor = ArgumentCaptor.forClass(FileChunk.class);
        verify(transactionQueue, times(2)).put(chunkCaptor.capture());
        List<FileChunk> chunks = chunkCaptor.getAllValues();

        assertEquals(4000, chunks.get(0).getLines().size());
        assertEquals(1, chunks.get(1).getLines().size());
        assertEquals(processingId, chunks.get(0).getFileProcessingId());
        assertEquals(processingId, chunks.get(1).getFileProcessingId());
        assertEquals(companyId, chunks.get(0).getCompanyId());
        assertEquals(companyId, chunks.get(1).getCompanyId());
    }
}