package com.finpulse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class WorkerPoolInitializer {

    private final ExecutorService workerThreadPool;

    private final BlockingQueue<List<String>> transactionQueue;
    
    private final TransactionRepository repository;

    private static final int DB_BATCH_SIZE = 500;

    private volatile boolean isRunning = true;


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
                    List<String> chunk=transactionQueue.poll(1,TimeUnit.SECONDS);
                    if(chunk!=null){
                        processChunk(chunk);
                    }
                }
                catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        }
        private void processChunk(List<String> chunk){

            List<Transaction> batch= new ArrayList<>();

            for(String line:chunk){
                try{
                    if(line==null || line.isBlank())continue;

                    String[] fields = line.split(",");

                    Transaction transaction = Transaction.builder()
                        .transactionId(fields[0])
                        .senderAccount(fields[1])
                        .receiverAccount(fields[2])
                        .amount(new BigDecimal(fields[3]))
                        .transactionType(fields[4])
                        .transactionTime(LocalDateTime.parse(fields[5]))
                        .status(ProcessingStatus.PENDING)
                        .build();

                    batch.add(transaction);

                    if (batch.size()==DB_BATCH_SIZE) {
                        repository.saveAll(batch);
                        batch.clear();   
                    }

                }
                catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }
            if (!batch.isEmpty()) {
                repository.saveAll(batch);
            }

        }
    }

    
}
