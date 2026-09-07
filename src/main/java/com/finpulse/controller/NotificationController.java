package com.finpulse.controller;

import com.finpulse.dto.ApiResponse;
import com.finpulse.entity.Notification;
import com.finpulse.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ledger/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Notification>>>  getAllNotifications(long companyId){

        List<Notification> notifications=notificationRepository.findByCompanyIdAndOrderByCreatedAtDesc(companyId);

        notifications.forEach(n->n.setViewed(true));
        notificationRepository.saveAll(notifications);
        return ResponseEntity.ok(ApiResponse.success(notifications, "Notifications retrieved"));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnReadData(long companyId){

        Long count=notificationRepository.countByCompanyIdAndViewedFalse(companyId);
        return ResponseEntity.ok(ApiResponse.success(count, "Unread count retrieved"));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearAll(long companyId){

        notificationRepository.deleteByCompanyId(companyId);
        return ResponseEntity.ok(ApiResponse.success(null, "All notifications cleared"));
    }

}
