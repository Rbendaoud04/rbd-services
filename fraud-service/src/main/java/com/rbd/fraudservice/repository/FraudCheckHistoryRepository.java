package com.rbd.fraudservice.repository;

import com.rbd.fraudservice.entity.FraudCheckHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FraudCheckHistoryRepository extends JpaRepository<FraudCheckHistory, Long> {

    // Spring Data JPA automatically implements this based on method name
    List<FraudCheckHistory> findByCustomerId(Long customerId);
}