package com.ticketing.booking.exception;

import org.springframework.http.HttpStatus;

public class BookingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;

    public BookingException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
