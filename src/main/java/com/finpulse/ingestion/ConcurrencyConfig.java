
package com.finpulse.ingestion;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConcurrencyConfig {

    private static final int THREAD_COUNT=Math.min
                           (Runtime.getRuntime().availableProcessors()*2,16);

    private static final int QUEUE_CAPACITY=20;

    @Bean
    public BlockingQueue<FileChunk> transactionQueue(){

        return new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService workerThreadPool(){
        return Executors.newFixedThreadPool(THREAD_COUNT);
    }

}