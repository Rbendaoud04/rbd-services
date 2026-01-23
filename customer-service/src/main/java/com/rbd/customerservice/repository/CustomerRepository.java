package com.rbd.customerservice.repository;


import com.rbd.customerservice.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // Custom finder method
    Optional<Customer> findByEmail(String email);

    // Check if email exists for validation purposes
    boolean existsByEmail(String email);
}