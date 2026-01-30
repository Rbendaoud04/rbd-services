package com.rbd.clients.notification;


public record NotificationRequest(
        Long toCustomerId,
        String toCustomerEmail,
        String sender,
        String message) {
}
