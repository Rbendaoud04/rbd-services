package com.rbd.fraudservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FraudCheckRequest {
    private Long customerId;
    private Boolean isFraudster;
}