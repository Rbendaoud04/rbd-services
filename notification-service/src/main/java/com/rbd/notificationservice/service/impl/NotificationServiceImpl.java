package com.rbd.notificationservice.service.impl;





import com.rbd.clients.notification.NotificationRequest;
import com.rbd.notificationservice.entity.Notification;
import com.rbd.notificationservice.repository.NotificationRepository;
import com.rbd.notificationservice.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @RabbitListener(queues = "customer.queue")
    public void sendNotification(NotificationRequest request) {

        log.info("trying to save asynchronous::\n{}", request);

        Notification notification = Notification.builder()
                .toCustomerEmail(request.toCustomerEmail())
                .toCustomerId(request.toCustomerId())
                .sender(request.sender())
                .message(request.message())
                .sentAt(LocalDateTime.now())
                .build();

        // Fire-and-forget
        notificationRepository.save(notification);
    }


    @Override
    public List<Notification> getNotificationsForCustomer(Long customerId) {
        return notificationRepository.findByToCustomerId(customerId);
    }

    @Override
    public Notification sendNotificationSynchronous(NotificationRequest request) {
        log.info("trying to save synchronous::\n"+ request);
        Notification notification = Notification.builder()
                .toCustomerEmail(request.toCustomerEmail())
                .toCustomerId(request.toCustomerId())
                .sender(request.sender())
                .message(request.message())
                .sentAt(LocalDateTime.now())
                .build();
        // For now synchronous: just save to DB (later integrate email/SMS)
        return notificationRepository.save(notification);
    }
}
