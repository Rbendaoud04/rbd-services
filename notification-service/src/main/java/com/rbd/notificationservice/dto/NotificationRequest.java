package com.rbd.notificationservice.dto;

public record NotificationRequest(
        Long toCustomerId,
        String toCustomerEmail,
        String sender,
        String message) {
}
