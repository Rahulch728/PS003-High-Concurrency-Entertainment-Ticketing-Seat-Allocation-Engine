package com.ticketing.booking.controller;

import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.exception.BookingException;
import com.ticketing.booking.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/bookings")
@CrossOrigin
public class BookingController {

    // The API Gateway validates the JWT and sends the user's id in this header.
    // The user id is never taken from the request body or query string.
    private static final String USER_ID_HEADER = "X-User-Id";

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Booking createBooking(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @RequestParam Long eventId,
            @RequestParam String seatNumber) {

        return bookingService.createBooking(
                userId,
                eventId,
                seatNumber);
    }

    @GetMapping("/my")
    public List<Booking> getMyBookings(
            @RequestHeader(USER_ID_HEADER) Long userId) {

        return bookingService.getBookingsByUser(userId);
    }

    @GetMapping("/user/{userId}")
    public List<Booking> getBookingsByUser(
            @RequestHeader(USER_ID_HEADER) Long currentUserId,
            @PathVariable Long userId) {

        if (!currentUserId.equals(userId)) {
            throw new BookingException(
                    HttpStatus.FORBIDDEN,
                    "You can only view your own bookings");
        }

        return bookingService.getBookingsByUser(userId);
    }

    @GetMapping("/event/{eventId}")
    public List<Booking> getBookingsByEvent(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long eventId) {

        // Only the caller's own bookings for this event are returned.
        return bookingService.getBookingsByEvent(eventId, userId);
    }
}
