package com.rbd.notificationservice.service.impl;





import com.rbd.notificationservice.entity.Notification;
import com.rbd.notificationservice.repository.NotificationRepository;
import com.rbd.notificationservice.service.NotificationService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public Notification sendNotification(Notification notification) {
        // For now synchronous: just save to DB (later integrate email/SMS)
        return notificationRepository.save(notification);
    }

    @Override
    public List<Notification> getNotificationsForCustomer(Long customerId) {
        return notificationRepository.findByToCustomerId(customerId);
    }
}
