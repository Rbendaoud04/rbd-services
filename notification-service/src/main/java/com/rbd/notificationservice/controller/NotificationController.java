package com.rbd.notificationservice.controller;


import com.rbd.notificationservice.dto.NotificationRequest;
import com.rbd.notificationservice.entity.Notification;
import com.rbd.notificationservice.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Send a notification
    @PostMapping
    public ResponseEntity<NotificationRequest> sendNotification(@RequestBody NotificationRequest request) {
        log.info("trying to save ::\n"+ request);
        Notification notification = Notification.builder()
                .toCustomerEmail(request.toCustomerEmail())
                .toCustomerId(request.toCustomerId())
                .sender(request.sender())
                .message(request.message())
                .sentAt(LocalDateTime.now())
                .build();
        Notification savedNotification = notificationService.sendNotification(notification);
        return ResponseEntity.ok(request);
    }

    // Get all notifications for a customer
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<Notification>> getNotifications(@PathVariable Long customerId) {
        return ResponseEntity.ok(notificationService.getNotificationsForCustomer(customerId));
    }
}
