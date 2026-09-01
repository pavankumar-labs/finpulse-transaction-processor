package com.finpulse.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class FileProcessingCompletedEvent {

    private final String fileProcessingId;
    private final Long companyId;
}
