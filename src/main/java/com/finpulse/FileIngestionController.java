package com.finpulse;

import java.io.IOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FileIngestionController {

    private final FileIngestionService service;

    @PostMapping(value = "/v1/ledger/upload", consumes = "multipart/form-data")
    public String ingestFile(@RequestPart("file") MultipartFile file) throws IOException{
        service.ingestFile(file);
        return "File uploaded successfully";
    }
    
}
