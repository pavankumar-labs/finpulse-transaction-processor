package com.finpulse.ingestion;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.finpulse.entity.ProcessingStatus;
import com.finpulse.entity.Transaction;
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

    private final ExecutorService workerThreadPool;

    private final BlockingQueue<FileChunk> transactionQueue;
    
     private final JdbcTemplate jdbcTemplate;

    private static final int DB_BATCH_SIZE = 500;

    private volatile boolean isRunning = true;

    private static final String INSERT_TRANSACTION_SQL =
        "INSERT IGNORE INTO transactions " +
        "(transaction_id, sender_account, receiver_account, amount, " +
        "transaction_type, transaction_time, status, file_name) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";


    @PostConstruct
    public void startWorkers(){
        int poolSize=((ThreadPoolExecutor)workerThreadPool).getCorePoolSize();
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

            List<Transaction> batch= new ArrayList<>();

            try{
                 for(String line:fileChunk.getLines()){
                try{
                    if(line==null || line.isBlank())continue;

                   Transaction transaction=validateAndBuildTransaction(line,fileChunk.getFileName());

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
                            "Unexpected error while processing row",
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
        }
        private void flushBatchToDatabase(List<Transaction> batch){
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
        }

        private Transaction validateAndBuildTransaction(String line,String fileName){

            String[] fields = line.split(",");
            BigDecimal amount;
            LocalDateTime transactionTime;
            if (fields.length!=6) {
                log.warn("file {} rejected invalid field count in line {}",
                    fileName,
                    line
                );
                return null;      
            }
            if(fields[0].isBlank() || fields[1].isBlank() || fields[2].isBlank()
            || fields[3].isBlank() || fields[4].isBlank()  ||  fields[5].isBlank()){

            log.warn("file {} rejected due to missing fields in line {}",
                line,
                fileName
            );
            return null;
            }
            try{
                amount=new BigDecimal(fields[3]);
               
            }
            catch(NumberFormatException e){
                log.warn(
                        "File {} rejected row due to invalid amount: {}",
                        fileName,
                        line
                    );
                return null;
            }
              if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("file {} rejected row due to negative ampunt: {}",fileName,
                    line
                );
                return null;
            }
            try{
                 transactionTime=LocalDateTime.parse(fields[5]);
            }
            catch(Exception e){
                log.warn("File {} rejected row due to invalid date: {}",
                    fileName,
                    line
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
                                    .status(ProcessingStatus.PENDING)
                                    .fileName(fileName)
                                    .build();


        }
    
    }

    
}
