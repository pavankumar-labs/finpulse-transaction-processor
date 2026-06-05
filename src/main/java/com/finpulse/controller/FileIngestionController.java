package com.finpulse.controller;


import java.util.List;

import com.finpulse.service.TransactionBatchCoordinator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FileIngestionController {

    private final TransactionBatchCoordinator coordinator;

    @PostMapping(value = "/v1/ledger/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> ingestFile(@RequestPart("file") List<MultipartFile> files) {
        try{
            for(MultipartFile file:files){
                if(!file.isEmpty()){
                    coordinator.streamFileContents(
                          file.getOriginalFilename(),
                        file.getInputStream()
                        
                    );
                }
            }
            return ResponseEntity.ok(
                "Files submitted to processing pipeline successfully."
            );
        }
        catch(Exception e){
             return ResponseEntity.status(500)
                .body("Error: " + e.getMessage());
        }
    }
    
}
