package com.rbd.clients.fraud;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient("FRAUD-SERVICE")
public interface FraudClient {
    @GetMapping(path = "/api/fraud/{customerID}")
    Boolean isFraudster(@PathVariable("customerID") Long customerID);

}
