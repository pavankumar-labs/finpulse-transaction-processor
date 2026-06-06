package com.finpulse.ingestion;

import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class FileChunk {

    private  final String fileProcessingId;
    private final String fileName;
    private final List<String> lines;
    
}
