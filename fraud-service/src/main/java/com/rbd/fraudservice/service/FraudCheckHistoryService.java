package com.rbd.fraudservice.service;

import com.rbd.fraudservice.dto.FraudCheckRequest;
import com.rbd.fraudservice.entity.FraudCheckHistory;
import java.util.List;

public interface FraudCheckHistoryService {
    boolean checkCustomerFraud(Long customerId);
    List<FraudCheckHistory> getFraudHistory(Long customerId);
    FraudCheckHistory saveFraudCheck(FraudCheckRequest request);
    List<FraudCheckHistory> getAllFraudChecks();
}