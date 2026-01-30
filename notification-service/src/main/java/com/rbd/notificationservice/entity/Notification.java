package com.rbd.notificationservice.entity;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    private Long toCustomerId;

    private String toCustomerEmail;

    private String sender;

    @Column(length = 1000)
    private String message;

    private LocalDateTime sentAt;

}
