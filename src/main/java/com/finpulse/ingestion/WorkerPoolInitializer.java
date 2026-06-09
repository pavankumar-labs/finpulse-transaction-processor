package com.finpulse.ingestion;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import com.finpulse.entity.ProcessingStatus;
import com.finpulse.entity.Transaction;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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

    private static final String INSERT_TRANSACTION_SQL =
        "INSERT IGNORE INTO transactions " +
        "(transaction_id, sender_account, receiver_account, amount, " +
        "transaction_type, transaction_time, status, file_name) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

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
        Gauge.builder("finpulse.active.threads",
                        workerThreadPool,ThreadPoolExecutor::getActiveCount)
                .description("Current active worker threads")
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
                    "Chunk processing started. fileProcessingId={}, fileName={}, rows={}",
                    fileChunk.getFileProcessingId(),
                    fileChunk.getFileName(),
                    fileChunk.getLines().size()
            );

            List<Transaction> batch= new ArrayList<>();

            try{
                 for(String line:fileChunk.getLines()){
                try{
                    if(line==null || line.isBlank())continue;
                    Transaction transaction=validateAndBuildTransaction
                            (fileChunk.getFileProcessingId(),line,fileChunk.getFileName());
                   if(transaction!=null){
                        batch.add(transaction);
                   }
                if (batch.size()==DB_BATCH_SIZE) {
                    flushBatchToDatabase(batch);
                    batch.clear();   
                }
                }
                catch (Exception e) {
                    log.error(
                            "Unexpected processing error. " +
                                    "fileProcessingId={}, fileName={}, row={}",
                            fileChunk.getFileProcessingId(),
                            fileChunk.getFileName(),
                            line,
                            e
                    );
                }

            }
            if (!batch.isEmpty()) {
                flushBatchToDatabase(batch);
            }

            }
            finally{
                batch.clear();
            }
            log.info(
                    "Chunk processing completed. fileProcessingId={}, fileName={}, rows={}",
                    fileChunk.getFileProcessingId(),
                    fileChunk.getFileName(),
                    fileChunk.getLines().size()
            );
        }

        private void flushBatchToDatabase(List<Transaction> batch){

            log.info(
                    "Batch insert started. size={}",
                    batch.size()
            );

            batchInsertTimer.record(()->{
                jdbcTemplate.batchUpdate(INSERT_TRANSACTION_SQL,batch,batch.size(),new ParameterizedPreparedStatementSetter<Transaction>() {
                    @Override
                    public void setValues(PreparedStatement ps,Transaction t) throws SQLException{
                        ps.setString(1, t.getTransactionId());
                        ps.setString(2, t.getSenderAccount());
                        ps.setString(3, t.getReceiverAccount());
                        ps.setBigDecimal(4, t.getAmount());
                        ps.setString(5, t.getTransactionType());
                        ps.setTimestamp(6,Timestamp.valueOf(t.getTransactionTime()) );
                        ps.setString(7, t.getStatus().name());
                        ps.setString(8, t.getFileName());
                    }
                });
            });

            log.info(
                    "Batch insert completed. size={}",
                    batch.size()
            );

        }

        private Transaction validateAndBuildTransaction(String fileProcessingId,String line,String fileName){

            String[] fields = line.split(",");
            BigDecimal amount;
            if (fields.length!=6) {
                log.warn(
                        "Transaction validation failed. " +
                                "fileProcessingId={}, fileName={}, reason={}, row={}",
                        fileProcessingId,
                        fileName,
                        "FIELD_SIZE_INVALID",
                        line
                );
                return null;      
            }
            if(fields[0].isBlank() || fields[1].isBlank() || fields[2].isBlank()
            || fields[3].isBlank() || fields[4].isBlank()  ||  fields[5].isBlank()){

            log.warn(
                    "Transaction validation failed. " +
                            "fileProcessingId={}, fileName={}, reason={}, transactionId={}",
                    fileProcessingId,
                    fileName,
                    "INSUFFICIENT_FIELDS",
                    fields[0]
            );
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
                return null;
            }
            LocalDateTime transactionTime = null;
            for (DateTimeFormatter formatter : FORMATTERS) {
                try {
                    transactionTime = LocalDateTime.parse(fields[5].trim(), formatter);
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
                return null;
            }

            return Transaction.builder()
                                    .transactionId(fields[0])
                                    .senderAccount(fields[1])
                                    .receiverAccount(fields[2])
                                    .amount(amount)
                                    .transactionType(fields[4])
                                    .transactionTime(transactionTime)
                                    .status(ProcessingStatus.PROCESSING)
                                    .fileName(fileName)
                                    .build();

        }
    }
}
