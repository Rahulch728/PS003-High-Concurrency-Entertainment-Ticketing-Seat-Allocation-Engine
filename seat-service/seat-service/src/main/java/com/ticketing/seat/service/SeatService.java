package com.ticketing.seat.service;

import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.exception.SeatNotAvailableException;
import com.ticketing.seat.exception.SeatNotFoundException;
import com.ticketing.seat.repository.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SeatService {

    private final SeatRepository seatRepository;

    public SeatService(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    public Seat createSeat(Seat seat) {

        if (seat.getStatus() == null ||
                seat.getStatus().isBlank()) {

            seat.setStatus("AVAILABLE");
        }

        seat.setLockedUntil(null);
        seat.setLockedBy(null);

        return seatRepository.save(seat);
    }

    @Transactional
    public List<Seat> getSeatsByEvent(Long eventId) {

        // Expired locks become AVAILABLE before the list is returned.
        seatRepository.releaseExpiredLocks(
                eventId,
                LocalDateTime.now()
        );

        return seatRepository.findByEventId(eventId);
    }

    @Transactional
    public Seat lockSeat(Long eventId, String seatNumber, Long userId) {

        LocalDateTime currentTime =
                LocalDateTime.now();

        LocalDateTime lockedUntil =
                currentTime.plusMinutes(10);

        int updatedRows =
                seatRepository.lockAvailableSeat(
                        eventId,
                        seatNumber,
                        lockedUntil,
                        currentTime,
                        userId
                );

        if (updatedRows == 0) {

            Seat seat = findSeat(eventId, seatNumber);

            if ("BOOKED".equals(seat.getStatus())) {
                throw new SeatNotAvailableException(
                        "Seat is already booked"
                );
            }

            if (userId.equals(seat.getLockedBy())) {
                throw new SeatNotAvailableException(
                        "You already hold the lock on this seat"
                );
            }

            throw new SeatNotAvailableException(
                    "Seat is currently locked by another user"
            );
        }

        return findSeat(eventId, seatNumber);
    }

    @Transactional
    public Seat bookSeat(Long eventId, String seatNumber, Long userId) {

        LocalDateTime currentTime =
                LocalDateTime.now();

        int updatedRows =
                seatRepository.bookLockedSeat(
                        eventId,
                        seatNumber,
                        userId,
                        currentTime
                );

        if (updatedRows == 0) {

            Seat seat = findSeat(eventId, seatNumber);

            if ("BOOKED".equals(seat.getStatus())) {
                throw new SeatNotAvailableException(
                        "Seat is already booked"
                );
            }

            if ("LOCKED".equals(seat.getStatus()) &&
                    seat.getLockedUntil() != null &&
                    seat.getLockedUntil().isBefore(currentTime)) {

                throw new SeatNotAvailableException(
                        "Seat lock has expired. Please lock the seat again"
                );
            }

            if ("LOCKED".equals(seat.getStatus())) {
                throw new SeatNotAvailableException(
                        "Seat is locked by another user"
                );
            }

            throw new SeatNotAvailableException(
                    "Seat is not locked. Lock the seat before booking"
            );
        }

        return findSeat(eventId, seatNumber);
    }

    // Called only by Booking Service when a booking could not be saved after
    // the seat was already marked BOOKED. Reverts the seat to AVAILABLE.
    @Transactional
    public void compensateSeat(Long eventId, String seatNumber, Long userId) {

        seatRepository.compensateBookedSeat(
                eventId,
                seatNumber,
                userId
        );
        // Best-effort: if nothing matched (already compensated, or the
        // seat state moved on), there is nothing further to safely undo.
    }

    @Transactional
    public Seat releaseSeat(Long eventId, String seatNumber, Long userId) {

        int updatedRows =
                seatRepository.releaseLockedSeat(
                        eventId,
                        seatNumber,
                        userId
                );

        if (updatedRows == 0) {

            findSeat(eventId, seatNumber);

            throw new SeatNotAvailableException(
                    "Only a seat locked by you can be released"
            );
        }

        return findSeat(eventId, seatNumber);
    }

    private Seat findSeat(Long eventId, String seatNumber) {

        return seatRepository
                .findByEventIdAndSeatNumber(
                        eventId,
                        seatNumber
                )
                .orElseThrow(() ->
                        new SeatNotFoundException(
                                "Seat not found"
                        ));
    }
}
