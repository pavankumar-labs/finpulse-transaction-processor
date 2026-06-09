package com.finpulse.controller;

import java.util.ArrayList;
import java.util.List;
import com.finpulse.dto.ApiResponse;
import com.finpulse.exception.InvalidFileException;
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
    public ResponseEntity<ApiResponse<List<String>>> ingestFile
            (@RequestPart("file") List<MultipartFile> files) throws Exception{

        if (files == null || files.isEmpty()) {
            throw new InvalidFileException("No files provided");
        }

        List<String> fileProcessingIds = new ArrayList<>();

            for(MultipartFile file:files){
                if (file.isEmpty()) {
                    throw new InvalidFileException
                            ("File is empty: " + file.getOriginalFilename());
                }
                String filename = file.getOriginalFilename();
                if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
                    throw new InvalidFileException(
                            "Only CSV files accepted. Received: " + filename);
                }
                try {
                    String fileProcessingId = coordinator.streamFileContents(
                            file.getOriginalFilename(),
                            file.getInputStream()
                    );
                    fileProcessingIds.add(fileProcessingId);
                } catch (Exception e) {
                    throw new InvalidFileException(
                            "Failed to process file: " + file.getOriginalFilename()
                    );
                }

            }
            return ResponseEntity.ok(ApiResponse.
                    success(fileProcessingIds,"Files submitted to processing pipeline successfully."
            ));

    }
    
}
