package com.finpulse.controller;

import com.finpulse.dto.ApiResponse;
import com.finpulse.entity.Notification;
import com.finpulse.repository.NotificationRepository;
import com.finpulse.security.FinPulseUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ledger/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('COMPANY_OWNER', 'COMPANY_MEMBER')")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Notification>>>  getAllNotifications(@AuthenticationPrincipal FinPulseUserDetails principal){

        Long companyId = principal.getCompanyId();

        List<Notification> notifications = notificationRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        notifications.forEach(n->n.setViewed(true));
        notificationRepository.saveAll(notifications);
        return ResponseEntity.ok(ApiResponse.success(notifications, "Notifications retrieved"));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnReadData(@AuthenticationPrincipal FinPulseUserDetails principal){

        Long count = notificationRepository.countByCompanyIdAndViewedFalse(principal.getCompanyId());
        return ResponseEntity.ok(ApiResponse.success(count, "Unread count retrieved"));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearAll(@AuthenticationPrincipal FinPulseUserDetails principal){

        notificationRepository.deleteByCompanyId(principal.getCompanyId());
        return ResponseEntity.ok(ApiResponse.success(null, "All notifications cleared"));
    }

}
