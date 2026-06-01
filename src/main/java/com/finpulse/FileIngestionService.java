package com.finpulse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileIngestionService{

    private final TransactionRepository repository;

    public void ingestFile(MultipartFile file) throws IOException{

        BufferedReader reader=new BufferedReader(new InputStreamReader(file.getInputStream()));

        String line;
        String headerLine =reader.readLine();

        List<Transaction> batch = new ArrayList<>();
        int batchSize = 500;

        while((line=reader.readLine())!=null){
            try{
                     String[] fields = line.split(",");
            Transaction transaction =Transaction.builder()
                                                .transactionId(fields[0])
                                                .senderAccount(fields[1])
                                                .receiverAccount(fields[2])
                                                .amount(new BigDecimal(fields[3]))
                                                .transactionType(fields[4])
                                                .status(ProcessingStatus.PENDING)
                                                .transactionTime(LocalDateTime.parse(fields[5]))
                                                .build();
            batch.add(transaction);
            if(batch.size()==batchSize){
                repository.saveAll(batch);
                batch.clear();
            }
            }
            catch(Exception e){
                System.err.println
                ("Skipping the bad line: "+line +" | Error: "+e.getMessage());

            }
        }
        if(!(batch.isEmpty())){
            repository.saveAll(batch);
        }
        reader.close();
    }
}