package com.eventledger.account.service;

public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(String accountId, String expected, String actual) {
        super("Currency mismatch for account " + accountId + ": expected " + expected + " but got " + actual);
    }
}
