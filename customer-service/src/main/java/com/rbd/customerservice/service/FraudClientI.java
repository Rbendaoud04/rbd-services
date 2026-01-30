package com.rbd.customerservice.service;


import java.util.concurrent.CompletableFuture;

public interface FraudClientI {

    CompletableFuture<Boolean> isFraud(Long customerId);

}
