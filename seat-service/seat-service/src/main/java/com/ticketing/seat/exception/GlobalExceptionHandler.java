package com.ticketing.seat.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SeatNotAvailableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleSeatNotAvailable(
            SeatNotAvailableException exception) {

        return Map.of(
                "message",
                exception.getMessage()
        );
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleForbidden(
            ForbiddenException exception) {

        return Map.of(
                "message",
                exception.getMessage()
        );
    }

    @ExceptionHandler(SeatNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleSeatNotFound(
            SeatNotFoundException exception) {

        return Map.of(
                "message",
                exception.getMessage()
        );
    }
}
