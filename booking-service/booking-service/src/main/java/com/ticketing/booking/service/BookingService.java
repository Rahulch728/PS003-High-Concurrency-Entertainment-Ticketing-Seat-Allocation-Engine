package com.ticketing.booking.service;

import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ticketing.booking.client.SeatClient;
import com.ticketing.booking.exception.BookingException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final SeatClient seatClient;

    public BookingService(
            BookingRepository bookingRepository,
            SeatClient seatClient) {
        this.bookingRepository = bookingRepository;
        this.seatClient = seatClient;
    }

    public Booking createBooking(
            Long userId,
            Long eventId,
            String seatNumber) {

        // Seat Service is the source of truth for seat state, and it is a
        // separate database from this service's, so the two writes below
        // cannot share one ACID transaction. If the booking row fails to
        // save after the seat is already BOOKED, we compensate by asking
        // Seat Service to revert the seat, rather than leaving a seat that
        // looks booked with no booking record behind it.
        SeatClient.SeatResponse seat =
                seatClient.bookSeat(eventId, seatNumber, userId);

        if (seat == null || !"BOOKED".equals(seat.getStatus())) {
            throw new BookingException(
                    HttpStatus.BAD_GATEWAY,
                    "Seat booking failed");
        }

        try {
            return saveBooking(userId, eventId, seatNumber, seat.getId());

        } catch (RuntimeException e) {

            log.error(
                    "Booking save failed after seat {} of event {} was marked BOOKED for user {}; compensating",
                    seatNumber, eventId, userId, e);

            boolean compensated =
                    seatClient.compensate(eventId, seatNumber, userId);

            if (compensated) {
                throw new BookingException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Booking could not be completed. The seat has been released, please try again");
            }

            // The seat could not be released automatically either. This is
            // the one case this architecture cannot fully self-heal:
            // surfaced clearly instead of silently leaving it inconsistent.
            throw new BookingException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Booking could not be completed and the seat could not be released automatically. Please contact support");
        }
    }

    @Transactional
    protected Booking saveBooking(
            Long userId, Long eventId, String seatNumber, Long seatId) {

        Booking booking = new Booking();

        booking.setUserId(userId);
        booking.setEventId(eventId);
        booking.setSeatId(seatId);
        booking.setSeatNumber(seatNumber);
        booking.setStatus("CONFIRMED");
        booking.setBookingTime(LocalDateTime.now());

        return bookingRepository.save(booking);
    }

    public List<Booking> getBookingsByUser(Long userId) {
        return bookingRepository.findByUserId(userId);
    }

    public List<Booking> getBookingsByEvent(Long eventId, Long userId) {
        return bookingRepository.findByEventIdAndUserId(eventId, userId);
    }
}
