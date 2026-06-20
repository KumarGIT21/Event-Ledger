package com.eventledger.gateway.client;

public class AccountServiceException extends RuntimeException {

    private final int statusCode;

    public AccountServiceException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public AccountServiceException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 503;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
