package com.rbd.customerservice.service.impl;

import com.rbd.customerservice.service.FraudClientI;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.CompletableFuture;

@Service
public class FraudClientImpl {}/*implements FraudClientI {

    private final WebClient webClient;

    public FraudClientImpl(WebClient fraudWebClient) {
        this.webClient = fraudWebClient;
    }

    @Override
    @CircuitBreaker(name = "fraudService", fallbackMethod = "fraudFallback")
    @TimeLimiter(name = "fraudService")
    public CompletableFuture<Boolean> isFraud(Long customerId) {

        return webClient.get()
                .uri("/api/fraud/{id}", customerId)
                .retrieve()
                .bodyToMono(Boolean.class)
                .toFuture();
    }

    // 🔥 MUST match method signature + Throwable
    private CompletableFuture<Boolean> fraudFallback(Long customerId, Throwable ex) {
        System.out.println("⚠ Fraud service unavailable for customer " + customerId);
        return CompletableFuture.completedFuture(false); // FAIL-OPEN
    }
}*/
