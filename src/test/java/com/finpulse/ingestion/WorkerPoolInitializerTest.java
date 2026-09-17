package com.finpulse.ingestion;

import com.finpulse.entity.AccountType;
import com.finpulse.entity.RejectedTransaction;
import com.finpulse.entity.RejectionStatus;
import com.finpulse.entity.Transaction;
import com.finpulse.event.FileProcessingCompletedEvent;
import com.finpulse.repository.RejectedTransactionRepository;
import com.finpulse.repository.UploadedFileRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerPoolInitializerTest {

    @Mock private ThreadPoolExecutor workerThreadPool;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private RejectedTransactionRepository rejectedTransactionRepository;
    @Mock private UploadedFileRepository uploadedFileRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    private BlockingQueue<FileChunk> transactionQueue;
    private SimpleMeterRegistry meterRegistry;
    private WorkerPoolInitializer workerPoolInitializer;

    @BeforeEach
    void setUp() {
        transactionQueue = new LinkedBlockingQueue<>();
        meterRegistry = new SimpleMeterRegistry();

        workerPoolInitializer = new WorkerPoolInitializer(
                meterRegistry,
                workerThreadPool,
                transactionQueue,
                jdbcTemplate,
                rejectedTransactionRepository,
                uploadedFileRepository,
                eventPublisher
        );
        workerPoolInitializer.startWorkers();
    }

    private FileChunk createFileChunk(List<String> lines) {
        return new FileChunk("file-123", "transactions.csv", lines, 1L);
    }

    private String validTransactionLine() {
        return "TXN001,ACC001,ACC002,1000.00,2026-09-15T10:30:00,personal,business";
    }

    private void invokeProcessChunk(FileChunk fileChunk) throws Exception {

        Class<?> workerTaskClass = null;
        for (Class<?> declaredClass : WorkerPoolInitializer.class.getDeclaredClasses()) {
            if (declaredClass.getSimpleName().equals("WorkerTask")) {
                workerTaskClass = declaredClass;
                break;
            }
        }
        assertNotNull(workerTaskClass, "WorkerTask inner class was not found");

        Constructor<?> constructor = workerTaskClass.getDeclaredConstructor(WorkerPoolInitializer.class);
        constructor.setAccessible(true);
        Object workerTask = constructor.newInstance(workerPoolInitializer);

        Method processChunk = workerTaskClass.getDeclaredMethod("processChunk", FileChunk.class);
        processChunk.setAccessible(true);
        processChunk.invoke(workerTask, fileChunk);
    }

    @Test
    void shouldProcessValidTransactionSuccessfully() throws Exception {
        FileChunk fileChunk = createFileChunk(List.of(validTransactionLine()));
        when(rejectedTransactionRepository.findByCompanyIdAndStatus(eq(1L), eq(RejectionStatus.PENDING)))
                .thenReturn(List.of());
        when(uploadedFileRepository.claimCompletionEvent("file-123")).thenReturn(0);

        invokeProcessChunk(fileChunk);

        verify(jdbcTemplate).batchUpdate(anyString(), anyList(), anyInt(), any());
        verify(uploadedFileRepository).incrementCompletedChunks("file-123");
        verify(uploadedFileRepository).claimCompletionEvent("file-123");
        verify(eventPublisher, never()).publishEvent(any());
        assertTrue(transactionQueue.isEmpty());
    }

    @Test
    void shouldRejectTransactionWhenFieldCountIsInvalid() throws Exception {
        String invalidLine = "TXN001,ACC001,ACC002,1000.00,2026-09-15T10:30:00";
        FileChunk fileChunk = createFileChunk(List.of(invalidLine));
        when(rejectedTransactionRepository.findByCompanyIdAndStatus(eq(1L), eq(RejectionStatus.PENDING)))
                .thenReturn(List.of());
        when(uploadedFileRepository.claimCompletionEvent("file-123")).thenReturn(0);

        invokeProcessChunk(fileChunk);

        ArgumentCaptor<RejectedTransaction> captor = ArgumentCaptor.forClass(RejectedTransaction.class);
        verify(rejectedTransactionRepository).save(captor.capture());
        RejectedTransaction rejected = captor.getValue();

        assertEquals("file-123", rejected.getFileProcessingId());
        assertEquals("transactions.csv", rejected.getFileName());
        assertEquals(1L, rejected.getCompanyId());
        assertEquals(invalidLine, rejected.getRawLine());
        assertEquals("FIELD_SIZE_INVALID", rejected.getReason());
        assertEquals(RejectionStatus.PENDING, rejected.getStatus());
        verify(jdbcTemplate, never()).batchUpdate(anyString(), anyList(), anyInt(), any());
        verify(uploadedFileRepository).incrementCompletedChunks("file-123");
    }

    @Test
    void shouldRejectTransactionWhenRequiredFieldIsBlank() throws Exception {
        String invalidLine = ",ACC001,ACC002,1000.00,2026-09-15T10:30:00,personal,business";
        FileChunk fileChunk = createFileChunk(List.of(invalidLine));
        when(rejectedTransactionRepository.findByCompanyIdAndStatus(eq(1L), eq(RejectionStatus.PENDING)))
                .thenReturn(List.of());
        when(uploadedFileRepository.claimCompletionEvent("file-123")).thenReturn(0);

        invokeProcessChunk(fileChunk);

        ArgumentCaptor<RejectedTransaction> captor = ArgumentCaptor.forClass(RejectedTransaction.class);
        verify(rejectedTransactionRepository).save(captor.capture());
        assertEquals("INSUFFICIENT_FIELDS", captor.getValue().getReason());
        verify(jdbcTemplate, never()).batchUpdate(anyString(), anyList(), anyInt(), any());
    }

    @Test
    void shouldRejectTransactionWhenAmountIsInvalid() throws Exception {
        String invalidLine = "TXN001,ACC001,ACC002,NOT_A_NUMBER,2026-09-15T10:30:00,personal,business";
        FileChunk fileChunk = createFileChunk(List.of(invalidLine));
        when(rejectedTransactionRepository.findByCompanyIdAndStatus(eq(1L), eq(RejectionStatus.PENDING)))
                .thenReturn(List.of());
        when(uploadedFileRepository.claimCompletionEvent("file-123")).thenReturn(0);

        invokeProcessChunk(fileChunk);

        ArgumentCaptor<RejectedTransaction> captor = ArgumentCaptor.forClass(RejectedTransaction.class);
        verify(rejectedTransactionRepository).save(captor.capture());
        assertEquals("INVALID_AMOUNT", captor.getValue().getReason());
        verify(jdbcTemplate, never()).batchUpdate(anyString(), anyList(), anyInt(), any());
    }

    @Test
    void shouldRejectTransactionWhenAmountIsZero() throws Exception {
        String invalidLine = "TXN001,ACC001,ACC002,0,2026-09-15T10:30:00,personal,business";
        FileChunk fileChunk = createFileChunk(List.of(invalidLine));
        when(rejectedTransactionRepository.findByCompanyIdAndStatus(eq(1L), eq(RejectionStatus.PENDING)))
                .thenReturn(List.of());
        when(uploadedFileRepository.claimCompletionEvent("file-123")).thenReturn(0);

        invokeProcessChunk(fileChunk);

        ArgumentCaptor<RejectedTransaction> captor = ArgumentCaptor.forClass(RejectedTransaction.class);
        verify(rejectedTransactionRepository).save(captor.capture());
        assertEquals("NON_POSITIVE_AMOUNT", captor.getValue().getReason());
        verify(jdbcTemplate, never()).batchUpdate(anyString(), anyList(), anyInt(), any());
    }

    @Test
    void shouldRejectTransactionWhenDateIsInvalid() throws Exception {
        String invalidLine = "TXN001,ACC001,ACC002,1000.00,invalid-date,personal,business";
        FileChunk fileChunk = createFileChunk(List.of(invalidLine));
        when(rejectedTransactionRepository.findByCompanyIdAndStatus(eq(1L), eq(RejectionStatus.PENDING)))
                .thenReturn(List.of());
        when(uploadedFileRepository.claimCompletionEvent("file-123")).thenReturn(0);

        invokeProcessChunk(fileChunk);

        ArgumentCaptor<RejectedTransaction> captor = ArgumentCaptor.forClass(RejectedTransaction.class);
        verify(rejectedTransactionRepository).save(captor.capture());
        assertEquals("INVALID_DATE", captor.getValue().getReason());
        verify(jdbcTemplate, never()).batchUpdate(anyString(), anyList(), anyInt(), any());
    }

    @Test
    void shouldUsePersonalAccountTypeWhenAccountTypeIsBlank() throws Exception {
        String line = "TXN001,ACC001,ACC002,1000.00,2026-09-15T10:30:00,,business";
        FileChunk fileChunk = createFileChunk(List.of(line));
        when(rejectedTransactionRepository.findByCompanyIdAndStatus(eq(1L), eq(RejectionStatus.PENDING)))
                .thenReturn(List.of());
        when(uploadedFileRepository.claimCompletionEvent("file-123")).thenReturn(0);

        List<Transaction> capturedBatch = new ArrayList<>();
        doAnswer(invocation -> {
            List<Transaction> batchArg = invocation.getArgument(1);
            capturedBatch.addAll(batchArg);
            return null;
        }).when(jdbcTemplate).batchUpdate(anyString(), anyList(), anyInt(), any());

        invokeProcessChunk(fileChunk);

        Transaction transaction = capturedBatch.get(0);
        assertEquals(AccountType.PERSONAL, transaction.getSenderAccountType());
        assertEquals(AccountType.BUSINESS, transaction.getReceiverAccountType());
    }

    @Test
    void shouldTreatUnknownAccountTypeAsPersonal() throws Exception {
        String line = "TXN001,ACC001,ACC002,1000.00,2026-09-15T10:30:00,unknown,business";
        FileChunk fileChunk = createFileChunk(List.of(line));
        when(rejectedTransactionRepository.findByCompanyIdAndStatus(eq(1L), eq(RejectionStatus.PENDING)))
                .thenReturn(List.of());
        when(uploadedFileRepository.claimCompletionEvent("file-123")).thenReturn(0);

        List<Transaction> capturedBatch = new ArrayList<>();
        doAnswer(invocation -> {
            List<Transaction> batchArg = invocation.getArgument(1);
            capturedBatch.addAll(batchArg);
            return null;
        }).when(jdbcTemplate).batchUpdate(anyString(), anyList(), anyInt(), any());

        invokeProcessChunk(fileChunk);

        Transaction transaction = capturedBatch.get(0);
        assertEquals(AccountType.PERSONAL, transaction.getSenderAccountType());
    }

    @Test
    void shouldResolveExistingPendingRejectionForValidTransaction() throws Exception {
        String transactionId = "TXN001";
        String line = transactionId + ",ACC001,ACC002,1000.00,2026-09-15T10:30:00,personal,business";
        RejectedTransaction pendingRejection = RejectedTransaction.builder()
                .transactionId(transactionId)
                .companyId(1L)
                .fileProcessingId("old-file")
                .fileName("old.csv")
                .rawLine(line)
                .reason("INVALID_DATE")
                .status(RejectionStatus.PENDING)
                .rejectedAt(LocalDateTime.now())
                .build();

        FileChunk fileChunk = createFileChunk(List.of(line));
        when(rejectedTransactionRepository.findByCompanyIdAndStatus(eq(1L), eq(RejectionStatus.PENDING)))
                .thenReturn(List.of(pendingRejection));
        when(uploadedFileRepository.claimCompletionEvent("file-123")).thenReturn(0);

        invokeProcessChunk(fileChunk);

        assertEquals(RejectionStatus.RESOLVED, pendingRejection.getStatus());
        assertNotNull(pendingRejection.getResolvedAt());
        verify(rejectedTransactionRepository).saveAll(List.of(pendingRejection));
        verify(jdbcTemplate).batchUpdate(anyString(), anyList(), anyInt(), any());
    }

    @Test
    void shouldPublishCompletionEventWhenCompletionEventIsClaimed() throws Exception {
        FileChunk fileChunk = createFileChunk(List.of(validTransactionLine()));
        when(rejectedTransactionRepository.findByCompanyIdAndStatus(eq(1L), eq(RejectionStatus.PENDING)))
                .thenReturn(List.of());
        when(uploadedFileRepository.claimCompletionEvent("file-123")).thenReturn(1);

        invokeProcessChunk(fileChunk);

        ArgumentCaptor<FileProcessingCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(FileProcessingCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        FileProcessingCompletedEvent event = eventCaptor.getValue();
        assertEquals("file-123", event.getFileProcessingId());
        assertEquals(1L, event.getCompanyId());
    }

    @Test
    void shouldNotPublishCompletionEventWhenEventWasAlreadyClaimed() throws Exception {
        FileChunk fileChunk = createFileChunk(List.of(validTransactionLine()));
        when(rejectedTransactionRepository.findByCompanyIdAndStatus(eq(1L), eq(RejectionStatus.PENDING)))
                .thenReturn(List.of());
        when(uploadedFileRepository.claimCompletionEvent("file-123")).thenReturn(0);

        invokeProcessChunk(fileChunk);
        verify(eventPublisher, never()).publishEvent(any(FileProcessingCompletedEvent.class));
    }

    @Test
    void shouldRequeueChunkWhenDatabaseBatchInsertFails() throws Exception {
        FileChunk fileChunk = createFileChunk(List.of(validTransactionLine()));
        when(rejectedTransactionRepository.findByCompanyIdAndStatus(eq(1L), eq(RejectionStatus.PENDING)))
                .thenReturn(List.of());
        doThrow(new RuntimeException("Database unavailable"))
                .when(jdbcTemplate).batchUpdate(anyString(), anyList(), anyInt(), any());

        invokeProcessChunk(fileChunk);

        assertEquals(1, transactionQueue.size());
        FileChunk requeuedChunk = transactionQueue.poll();
        assertSame(fileChunk, requeuedChunk);
        verify(uploadedFileRepository, never()).incrementCompletedChunks(anyString());
        verify(uploadedFileRepository, never()).claimCompletionEvent(anyString());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldSkipBlankLines() throws Exception {
        FileChunk fileChunk = createFileChunk(List.of("", "   ", validTransactionLine()));
        when(rejectedTransactionRepository.findByCompanyIdAndStatus(eq(1L), eq(RejectionStatus.PENDING)))
                .thenReturn(List.of());
        when(uploadedFileRepository.claimCompletionEvent("file-123")).thenReturn(0);

        List<Transaction> capturedBatch = new ArrayList<>();
        doAnswer(invocation -> {
            List<Transaction> batchArg = invocation.getArgument(1);
            capturedBatch.addAll(batchArg);
            return null;
        }).when(jdbcTemplate).batchUpdate(anyString(), anyList(), anyInt(), any());

        invokeProcessChunk(fileChunk);

        assertEquals(1, capturedBatch.size());
    }

    @Test
    void shouldProcessSupportedDateFormatWithSpaceSeparator() throws Exception {
        String line = "TXN001,ACC001,ACC002,1000.00,2026-09-15 10:30:00,personal,business";
        FileChunk fileChunk = createFileChunk(List.of(line));
        when(rejectedTransactionRepository.findByCompanyIdAndStatus(eq(1L), eq(RejectionStatus.PENDING)))
                .thenReturn(List.of());
        when(uploadedFileRepository.claimCompletionEvent("file-123")).thenReturn(0);

        invokeProcessChunk(fileChunk);
        verify(jdbcTemplate).batchUpdate(anyString(), anyList(), anyInt(), any());
    }

    @Test
    void shouldFlushBatchWhenExactly500ValidTransactionsArePresent() throws Exception {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            lines.add("TXN" + i + ",ACC001,ACC002,100.00,2026-09-15T10:30:00,personal,business");
        }

        FileChunk fileChunk = createFileChunk(lines);
        when(rejectedTransactionRepository.findByCompanyIdAndStatus(eq(1L), eq(RejectionStatus.PENDING)))
                .thenReturn(List.of());
        when(uploadedFileRepository.claimCompletionEvent("file-123")).thenReturn(0);

        invokeProcessChunk(fileChunk);

        verify(jdbcTemplate, times(1)).batchUpdate(anyString(), anyList(), eq(500), any());
        verify(uploadedFileRepository).incrementCompletedChunks("file-123");
    }
}