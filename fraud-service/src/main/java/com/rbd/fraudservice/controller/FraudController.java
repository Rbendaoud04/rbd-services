package com.rbd.fraudservice.controller;

import com.rbd.fraudservice.dto.FraudCheckRequest;
import com.rbd.fraudservice.entity.FraudCheckHistory;
import com.rbd.fraudservice.service.FraudCheckHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/fraud")
@RequiredArgsConstructor
@Slf4j
public class FraudController {

    private final FraudCheckHistoryService fraudService;

    // GET /api/fraud/{customerId}
    @GetMapping("/{customerId}")
    public ResponseEntity<Boolean> isFraudster(@PathVariable Long customerId) throws InterruptedException{
        log.info("API request: Check if customer {} is fraudster", customerId);
        //Thread.sleep(10000);
        boolean isFraud = fraudService.checkCustomerFraud(customerId);
        return ResponseEntity.ok(isFraud);
    }

    // GET /api/fraud/history/{customerId}
    @GetMapping("/history/{customerId}")
    public ResponseEntity<List<FraudCheckHistory>> getFraudHistory(@PathVariable Long customerId) {
        log.info("API request: Get history for customer {}", customerId);
        return ResponseEntity.ok(fraudService.getFraudHistory(customerId));
    }

    // POST /api/fraud
    @PostMapping
    public ResponseEntity<FraudCheckHistory> registerFraudCheck(@RequestBody FraudCheckRequest request) {
        log.info("API request: Register new fraud check");
        FraudCheckHistory savedCheck = fraudService.saveFraudCheck(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedCheck);
    }

    // GET /api/fraud
    @GetMapping
    public ResponseEntity<List<FraudCheckHistory>> getAllFraudChecks() {
        log.info("API request: Get all fraud checks");
        return ResponseEntity.ok(fraudService.getAllFraudChecks());
    }
}