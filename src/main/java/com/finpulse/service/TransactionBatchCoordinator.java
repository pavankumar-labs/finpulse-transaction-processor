package com.finpulse.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

import com.finpulse.ingestion.FileChunk;
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

     @PostConstruct
     public void initializeMetrics(){
         filesReceivedCounter=Counter.builder("finpulse.files.received.total")
                 .description("Total files received for ingestion")
                 .register(registry);
     }

    public void streamFileContents(String fileName,InputStream fileInputStream)
                                            throws IOException,InterruptedException{

         filesReceivedCounter.increment();
        String fileProcessingId =
                UUID.randomUUID().toString();
        log.info(
                "File ingestion started. fileProcessingId={}, fileName={}",
                fileProcessingId,
                fileName
        );
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(fileInputStream))){

            reader.readLine();

            String line;
            List<String> currentChunk=new ArrayList<>(CHUNK_SIZE);

            while ((line=reader.readLine())!=null) {
                currentChunk.add(line);
                if(currentChunk.size()==CHUNK_SIZE){
                    transactionQueue.put(new FileChunk(fileProcessingId,fileName,currentChunk));
                    currentChunk=new ArrayList<>(CHUNK_SIZE);
                }   
            }

            if (!currentChunk.isEmpty()) {
                transactionQueue.put(new FileChunk(fileProcessingId,fileName, currentChunk));
            }
        }
       
}
}