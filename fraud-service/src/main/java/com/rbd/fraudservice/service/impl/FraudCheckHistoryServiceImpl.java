package com.rbd.fraudservice.service.impl;

import com.rbd.fraudservice.dto.FraudCheckRequest;
import com.rbd.fraudservice.entity.FraudCheckHistory;
import com.rbd.fraudservice.exception.FraudCheckNotFoundException;
import com.rbd.fraudservice.repository.FraudCheckHistoryRepository;
import com.rbd.fraudservice.service.FraudCheckHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor // Generates constructor for dependency injection
@Slf4j
public class FraudCheckHistoryServiceImpl implements FraudCheckHistoryService {

    private final FraudCheckHistoryRepository repository;

    @Override
    public boolean checkCustomerFraud(Long customerId) {
        log.info("Checking fraud status for customer {}", customerId);
        List<FraudCheckHistory> history = repository.findByCustomerId(customerId);

        // Business Logic: If any record marks them as a fraudster, return true
        return history.stream().anyMatch(FraudCheckHistory::getIsFraudstr);
    }

    @Override
    public List<FraudCheckHistory> getFraudHistory(Long customerId) {
        log.info("Retrieving history for customer {}", customerId);
        List<FraudCheckHistory> history = repository.findByCustomerId(customerId);

        if (history.isEmpty()) {
            // Throw custom exception if no history exists (optional business rule)
            throw new FraudCheckNotFoundException("No fraud history found for customer " + customerId);
        }
        return history;
    }

    @Override
    public FraudCheckHistory saveFraudCheck(FraudCheckRequest request) {
        log.info("Saving fraud check for customer {}", request.getCustomerId());

        FraudCheckHistory fraudCheck = FraudCheckHistory.builder()
                .customerId(request.getCustomerId())
                .isFraudstr(request.getIsFraudster())
                .build();

        return repository.save(fraudCheck);
    }

    @Override
    public List<FraudCheckHistory> getAllFraudChecks() {
        log.info("Retrieving all fraud checks");
        return repository.findAll();
    }
}