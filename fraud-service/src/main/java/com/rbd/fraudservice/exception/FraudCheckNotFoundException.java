package com.rbd.fraudservice.exception;

public class FraudCheckNotFoundException extends RuntimeException {
    public FraudCheckNotFoundException(String message) {
        super(message);
    }
}