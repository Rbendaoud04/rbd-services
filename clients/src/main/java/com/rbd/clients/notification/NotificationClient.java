package com.rbd.clients.notification;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("NOTIFICATION-SERVICE")
public interface NotificationClient {
    @PostMapping(path = "/api/notifications")
    NotificationRequest sendNotification(@RequestBody NotificationRequest request);
}
