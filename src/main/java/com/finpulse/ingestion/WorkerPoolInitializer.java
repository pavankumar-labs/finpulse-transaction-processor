package com.finpulse.ingestion;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.finpulse.entity.*;
import com.finpulse.event.FileProcessingCompletedEvent;
import com.finpulse.repository.RejectedTransactionRepository;
import com.finpulse.repository.UploadedFileRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@RequiredArgsConstructor
@Component
@Slf4j
public class WorkerPoolInitializer {

    private final MeterRegistry meterRegistry;
    private Timer batchInsertTimer;
    private final ThreadPoolExecutor workerThreadPool;
    private final BlockingQueue<FileChunk> transactionQueue;
    private final JdbcTemplate jdbcTemplate;
    private static final int DB_BATCH_SIZE = 500;
    private volatile boolean isRunning = true;
    private final RejectedTransactionRepository rejectedTransactionRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final ApplicationEventPublisher eventPublisher;


    private static final String INSERT_TRANSACTION_SQL =
        "INSERT IGNORE INTO transactions " +
        "(transaction_id, sender_account, receiver_account, amount, " +
        "transaction_time, file_name, company_id, file_processing_id, sender_account_type, receiver_account_type) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    );

    @PostConstruct
    public void startWorkers(){
        batchInsertTimer=Timer.builder("finpulse.batch.insert.duration")
                .description("Time taken to insert transaction batches")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
        int poolSize=workerThreadPool.getCorePoolSize();
        for(int i=0; i<poolSize;i++){
            workerThreadPool.submit(new WorkerTask());
        }

    }

    @PreDestroy
    public void stopWorkers(){
        isRunning=false;
    }

    private class WorkerTask implements Runnable{

        @Override
        public void run(){
            while (isRunning || !transactionQueue.isEmpty()) {
                try{
                    FileChunk fileChunk=transactionQueue.poll(1,TimeUnit.SECONDS);
                    if(fileChunk!=null){
                        processChunk(fileChunk);
                    }
                }
                catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        }

        private void processChunk(FileChunk fileChunk){
            log.info(
                    "Chunk processing started. fileProcessingId={}, fileName={}, rows={}, companyID={}",
                    fileChunk.getFileProcessingId(),
                    fileChunk.getFileName(),
                    fileChunk.getLines().size(),
                    fileChunk.getCompanyId()
            );

            Map<String, List<RejectedTransaction>> pendingRejections=
                    rejectedTransactionRepository.findByCompanyIdAndStatus(fileChunk.getCompanyId(), RejectionStatus.PENDING)
                            .stream()
                            .collect(Collectors.groupingBy(RejectedTransaction::getTransactionId));

            List<Transaction> batch= new ArrayList<>();

            boolean chunkSucceeded = true;
            try{
                 for(String line:fileChunk.getLines()){
                try{
                    if(line==null || line.isBlank())continue;
                    Transaction transaction=validateAndBuildTransaction
                            (fileChunk.getFileProcessingId(),line,fileChunk.getFileName(),fileChunk.getCompanyId());
                   if(transaction!=null){
                       List<RejectedTransaction> matches=pendingRejections.get(transaction.getTransactionId());
                       if(matches!=null){
                           for (RejectedTransaction matched: matches){
                               matched.setStatus(RejectionStatus.RESOLVED);
                               matched.setResolvedAt(LocalDateTime.now());
                           }
                           rejectedTransactionRepository.saveAll(matches);
                           pendingRejections.remove(transaction.getTransactionId());
                       }
                        batch.add(transaction);
                   }
                if (batch.size()==DB_BATCH_SIZE) {
                    try {
                        flushBatchToDatabase(batch);
                        batch.clear();
                    } catch (Exception e) {
                        chunkSucceeded = false;
                        break;
                    }
                }
                }
                catch (Exception e) {
                    chunkSucceeded = false;
                    log.error(
                            "Unexpected processing error. " +
                                    "fileProcessingId={}, fileName={}, row={}",
                            fileChunk.getFileProcessingId(),
                            fileChunk.getFileName(),
                            line,
                            e
                    );
                    break;
                }

            }
            if (chunkSucceeded && !batch.isEmpty()) {
               try {
                   flushBatchToDatabase(batch);
               } catch (Exception e) {
                   chunkSucceeded = false;
                   log.error( "Failed to persist final transaction batch. " +
                           "fileProcessingId={}, fileName={}, batchSize={}",
                           fileChunk.getFileProcessingId(), fileChunk.getFileName(),
                           batch.size(), e );
               }
            }

            }
            finally{
                batch.clear();
            }
            if (!chunkSucceeded) { log.error( "Chunk processing failed. Chunk will not be marked completed. " +
                    "fileProcessingId={}, fileName={}",
                    fileChunk.getFileProcessingId(), fileChunk.getFileName() );
                try {

                    transactionQueue.put(fileChunk);

                    log.info(
                            "Failed chunk re-queued successfully. " +
                                    "fileProcessingId={}, fileName={}",
                            fileChunk.getFileProcessingId(),
                            fileChunk.getFileName()
                    );

                } catch (InterruptedException e){
                    Thread.currentThread().interrupt();
                }
                return; }

            log.info(
                    "Chunk processing completed. fileProcessingId={}, fileName={}, rows={}",
                    fileChunk.getFileProcessingId(),
                    fileChunk.getFileName(),
                    fileChunk.getLines().size()
            );

            uploadedFileRepository.incrementCompletedChunks(
                    fileChunk.getFileProcessingId()
            );

            int eventClaimed =
                    uploadedFileRepository.claimCompletionEvent(
                            fileChunk.getFileProcessingId()
                    );

            if (eventClaimed == 1) {
                eventPublisher.publishEvent(
                        new FileProcessingCompletedEvent(
                                fileChunk.getFileProcessingId(),
                                fileChunk.getCompanyId()
                        )
                );
            }

        }

        private void flushBatchToDatabase(List<Transaction> batch){


            batchInsertTimer.record(()->{
                jdbcTemplate.batchUpdate(INSERT_TRANSACTION_SQL,batch,batch.size(),new ParameterizedPreparedStatementSetter<Transaction>() {
                    @Override
                    public void setValues(PreparedStatement ps,Transaction t) throws SQLException{
                        ps.setString(1, t.getTransactionId());
                        ps.setString(2, t.getSenderAccount());
                        ps.setString(3, t.getReceiverAccount());
                        ps.setBigDecimal(4, t.getAmount());
                        ps.setTimestamp(5,Timestamp.valueOf(t.getTransactionTime()) );
                        ps.setString(6, t.getFileName());
                        ps.setLong(7, t.getCompanyId());
                        ps.setString(8,t.getFileProcessingId());
                        ps.setString(9, t.getSenderAccountType().name());
                        ps.setString(10, t.getReceiverAccountType().name());
                    }
                });
            });

        }

        private Transaction validateAndBuildTransaction(String fileProcessingId,String line,String fileName, Long companyId){


            String[] fields = line.split(",",-1);
            BigDecimal amount;
            if (fields.length!=7) {
                log.warn(
                        "Transaction validation failed. " +
                                "fileProcessingId={}, fileName={}, reason={}, row={}",
                        fileProcessingId,
                        fileName,
                        "FIELD_SIZE_INVALID",
                        line
                );
                saveRejectedTransaction(fileProcessingId,fileName,companyId,line,"FIELD_SIZE_INVALID",null);
                return null;      
            }
            if(fields[0].isBlank() || fields[1].isBlank() || fields[2].isBlank()
            || fields[3].isBlank() || fields[4].isBlank()){

            log.warn(
                    "Transaction validation failed. " +
                            "fileProcessingId={}, fileName={}, reason={}, transactionId={}",
                    fileProcessingId,
                    fileName,
                    "INSUFFICIENT_FIELDS",
                    fields[0]
            );
            saveRejectedTransaction(fileProcessingId, fileName, companyId, line, "INSUFFICIENT_FIELDS", fields[0]);
            return null;
            }
            try{
                amount=new BigDecimal(fields[3]);
               
            }
            catch(NumberFormatException e){
                log.warn(
                        "Transaction validation failed. " +
                                "fileProcessingId={}, fileName={}, reason={}, transactionId={}",
                        fileProcessingId,
                        fileName,
                        "INVALID_AMOUNT",
                        fields[0]

                );
                saveRejectedTransaction(fileProcessingId, fileName, companyId, line, "INVALID_AMOUNT", fields[0]);
                return null;
            }
              if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                  log.warn(
                          "Transaction validation failed. " +
                                  "fileProcessingId={}, fileName={}, reason={}, transactionId={},amount={}",
                          fileProcessingId,
                          fileName,
                          "NON_POSITIVE_AMOUNT",
                          fields[0],
                          fields[3]
                  );
                  saveRejectedTransaction(fileProcessingId, fileName, companyId, line, "NON_POSITIVE_AMOUNT", fields[0]);
                return null;
            }
            LocalDateTime transactionTime = null;
            for (DateTimeFormatter formatter : FORMATTERS) {
                try {
                    transactionTime = LocalDateTime.parse(fields[4].trim(), formatter);
                    break;
                } catch (Exception ignored) {
                }
            }
            if (transactionTime == null) {
                log.warn(
                        "Transaction validation failed. " +
                                "fileProcessingId={}, fileName={}, reason={}, transactionId={}",
                        fileProcessingId, fileName, "INVALID_DATE", fields[0]
                );
                saveRejectedTransaction(fileProcessingId, fileName, companyId, line, "INVALID_DATE", fields[0]);
                return null;
            }

            AccountType senderAccountType = parseAccountType(fields[5]);
            AccountType receiverAccountType = parseAccountType(fields[6]);

            return Transaction.builder()
                                    .transactionId(fields[0])
                                    .senderAccount(fields[1])
                                    .receiverAccount(fields[2])
                                    .amount(amount)
                                    .transactionTime(transactionTime)
                                    .fileName(fileName)
                                    .companyId(companyId)
                                    .fileProcessingId(fileProcessingId)
                                    .senderAccountType(senderAccountType)
                                    .receiverAccountType(receiverAccountType)
                                    .build();

        }

        private void saveRejectedTransaction
                (String fileProcessingId,String fileName, Long companyId,
                 String rawLine, String reason, String transactionId){
            RejectedTransaction rejected=RejectedTransaction.builder()
                    .fileProcessingId(fileProcessingId)
                    .fileName(fileName)
                    .companyId(companyId)
                    .transactionId(transactionId)
                    .rawLine(rawLine)
                    .status(RejectionStatus.PENDING)
                    .reason(reason)
                    .rejectedAt(LocalDateTime.now())
                    .build();
            rejectedTransactionRepository.save(rejected);
        }

        private AccountType parseAccountType(String raw){
            if(raw.isBlank()){
                return AccountType.PERSONAL;
            }
            return raw.trim().equalsIgnoreCase("business")?AccountType.BUSINESS:AccountType.PERSONAL;
        }
    }
}
