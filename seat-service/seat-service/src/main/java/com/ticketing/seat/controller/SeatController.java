package com.ticketing.seat.controller;

import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.exception.ForbiddenException;
import com.ticketing.seat.service.SeatService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/seats")
@CrossOrigin
public class SeatController {

    // The API Gateway validates the JWT and sends the user's id and role
    // in these headers.
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Seat createSeat(
            @RequestHeader(value = USER_ROLE_HEADER, required = false) String role,
            @RequestBody Seat seat) {

        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException(
                    "Only an administrator can manage seats");
        }
        return seatService.createSeat(seat);
    }

    @GetMapping("/event/{eventId}")
    public List<Seat> getSeatsByEvent(
            @PathVariable Long eventId) {

        return seatService.getSeatsByEvent(eventId);
    }

    @PutMapping("/lock/{eventId}/{seatNumber}")
    public Seat lockSeat(
            @PathVariable Long eventId,
            @PathVariable String seatNumber,
            @RequestHeader(USER_ID_HEADER) Long userId) {

        return seatService.lockSeat(eventId, seatNumber, userId);
    }

    @PutMapping("/book/{eventId}/{seatNumber}")
    public Seat bookSeat(
            @PathVariable Long eventId,
            @PathVariable String seatNumber,
            @RequestHeader(USER_ID_HEADER) Long userId) {

        return seatService.bookSeat(eventId, seatNumber, userId);
    }

    @PutMapping("/release/{eventId}/{seatNumber}")
    public Seat releaseSeat(
            @PathVariable Long eventId,
            @PathVariable String seatNumber,
            @RequestHeader(USER_ID_HEADER) Long userId) {

        return seatService.releaseSeat(eventId, seatNumber, userId);
    }

    // Internal: called by Booking Service only, to undo a BOOKED seat when
    // saving the booking record failed. Not used by the frontend.
    @PutMapping("/compensate/{eventId}/{seatNumber}")
    public void compensateSeat(
            @PathVariable Long eventId,
            @PathVariable String seatNumber,
            @RequestHeader(USER_ID_HEADER) Long userId) {

        seatService.compensateSeat(eventId, seatNumber, userId);
    }
}
