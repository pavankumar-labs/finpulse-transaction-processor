package com.finpulse.listener;

import com.finpulse.entity.Notification;
import com.finpulse.entity.RejectionStatus;
import com.finpulse.event.FileProcessingCompletedEvent;
import com.finpulse.repository.NotificationRepository;
import com.finpulse.repository.RejectedTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class FileProcessedNotificationListener {

    private final RejectedTransactionRepository rejectedTransactionRepository;
    private final NotificationRepository notificationRepository;

    @Async("notificationExecutor")
    @EventListener
    public void onFileProcessingCompleted(FileProcessingCompletedEvent event){

        long rejectedCount=rejectedTransactionRepository
                .countByCompanyIdAndFileProcessingIdAndStatus(event.getCompanyId(),event.getFileProcessingId(), RejectionStatus.PENDING);

        long resolvedCount=rejectedTransactionRepository
                .countByCompanyIdAndFileProcessingIdAndStatus(event.getCompanyId(), event.getFileProcessingId(), RejectionStatus.RESOLVED);

        String message = String.format(
                "File processed: %d row(s) rejected, %d row(s) resolved.", rejectedCount, resolvedCount);

        Notification notification = Notification.builder()
                .companyId(event.getCompanyId())
                .message(message)
                .referenceId(event.getFileProcessingId())
                .viewed(false)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
    }
}
