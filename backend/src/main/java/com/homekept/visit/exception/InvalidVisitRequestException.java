package com.homekept.visit.exception;

/**
 * Thrown for invalid visit request parameters (bad enum value, missing field, etc.).
 * Maps to HTTP 400 Bad Request.
 */
public class InvalidVisitRequestException extends RuntimeException {

    public InvalidVisitRequestException(String message) {
        super(message);
    }
}
