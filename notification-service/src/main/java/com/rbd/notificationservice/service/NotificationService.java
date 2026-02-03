package com.rbd.notificationservice.service;



import com.rbd.clients.notification.NotificationRequest;
import com.rbd.notificationservice.entity.Notification;

import java.util.List;

public interface NotificationService {
    void sendNotification(NotificationRequest reques);
    List<Notification> getNotificationsForCustomer(Long customerId);

    Notification sendNotificationSynchronous(NotificationRequest request);
}
