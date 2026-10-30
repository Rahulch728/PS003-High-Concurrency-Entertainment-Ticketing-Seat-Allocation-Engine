package com.ticketing.seat.repository;

import com.ticketing.seat.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByEventId(Long eventId);

    Optional<Seat> findByEventIdAndSeatNumber(
            Long eventId,
            String seatNumber
    );

    // Atomic lock: a single conditional UPDATE decides the winner in the
    // database, so two users can never both lock the same seat.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Seat s
        SET s.status = 'LOCKED',
            s.lockedUntil = :lockedUntil,
            s.lockedBy = :userId
        WHERE s.eventId = :eventId
        AND s.seatNumber = :seatNumber
        AND (
            s.status = 'AVAILABLE'
            OR (
                s.status = 'LOCKED'
                AND s.lockedUntil < :currentTime
            )
        )
    """)
    int lockAvailableSeat(
            @Param("eventId") Long eventId,
            @Param("seatNumber") String seatNumber,
            @Param("lockedUntil") LocalDateTime lockedUntil,
            @Param("currentTime") LocalDateTime currentTime,
            @Param("userId") Long userId
    );

    // Atomic booking: only the user who holds a still-valid lock can book.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Seat s
        SET s.status = 'BOOKED',
            s.lockedUntil = null,
            s.lockedBy = null,
            s.bookedBy = :userId
        WHERE s.eventId = :eventId
        AND s.seatNumber = :seatNumber
        AND s.status = 'LOCKED'
        AND s.lockedBy = :userId
        AND s.lockedUntil >= :currentTime
    """)
    int bookLockedSeat(
            @Param("eventId") Long eventId,
            @Param("seatNumber") String seatNumber,
            @Param("userId") Long userId,
            @Param("currentTime") LocalDateTime currentTime
    );

    // Compensation: undoes a BOOKED seat back to AVAILABLE when the Booking
    // Service could not persist the booking record. Scoped to the same user
    // who was just booked in, so a stray/duplicate call cannot release a
    // seat that belongs to someone else's successful booking.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Seat s
        SET s.status = 'AVAILABLE',
            s.bookedBy = null
        WHERE s.eventId = :eventId
        AND s.seatNumber = :seatNumber
        AND s.status = 'BOOKED'
        AND s.bookedBy = :userId
    """)
    int compensateBookedSeat(
            @Param("eventId") Long eventId,
            @Param("seatNumber") String seatNumber,
            @Param("userId") Long userId
    );

    // Only the lock owner can release their own lock.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Seat s
        SET s.status = 'AVAILABLE',
            s.lockedUntil = null,
            s.lockedBy = null
        WHERE s.eventId = :eventId
        AND s.seatNumber = :seatNumber
        AND s.status = 'LOCKED'
        AND s.lockedBy = :userId
    """)
    int releaseLockedSeat(
            @Param("eventId") Long eventId,
            @Param("seatNumber") String seatNumber,
            @Param("userId") Long userId
    );

    // Turns expired locks back into AVAILABLE so listings are always accurate.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Seat s
        SET s.status = 'AVAILABLE',
            s.lockedUntil = null,
            s.lockedBy = null
        WHERE s.eventId = :eventId
        AND s.status = 'LOCKED'
        AND s.lockedUntil < :currentTime
    """)
    int releaseExpiredLocks(
            @Param("eventId") Long eventId,
            @Param("currentTime") LocalDateTime currentTime
    );
}
