package com.finpulse.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import com.finpulse.ingestion.FileChunk;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionBatchCoordinator{

    private final BlockingQueue<FileChunk> transactionQueue;

     private static final int CHUNK_SIZE = 4000;

    public void streamFileContents(String fileName,InputStream fileInputStream)
                                            throws IOException,InterruptedException{


        try(BufferedReader reader=new BufferedReader(new InputStreamReader(fileInputStream))){

            reader.readLine();

            String line;
            List<String> currentChunk=new ArrayList<>(CHUNK_SIZE);

            while ((line=reader.readLine())!=null) {
                currentChunk.add(line);
                if(currentChunk.size()==CHUNK_SIZE){
                    transactionQueue.put(new FileChunk(fileName,currentChunk));
                    currentChunk=new ArrayList<>(CHUNK_SIZE);
                }   
            }

            if (!currentChunk.isEmpty()) {
                transactionQueue.put(new FileChunk(fileName, currentChunk));
            }
        }
       
}
}