package com.rbd.notificationservice.service;



import com.rbd.notificationservice.entity.Notification;

import java.util.List;

public interface NotificationService {
    Notification sendNotification(Notification notification);
    List<Notification> getNotificationsForCustomer(Long customerId);
}
