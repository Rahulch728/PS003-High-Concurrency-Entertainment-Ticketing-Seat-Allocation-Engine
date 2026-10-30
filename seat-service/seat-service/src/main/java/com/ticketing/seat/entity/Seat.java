package com.ticketing.seat.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "seats",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"event_id", "seat_number"})
        }
)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @NotBlank
    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @NotBlank
    @Column(nullable = false)
    private String status;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    // Id of the user who currently holds the lock (null when not locked)
    @Column(name = "locked_by")
    private Long lockedBy;

    // Id of the user whose booking currently occupies this seat (set when
    // status becomes BOOKED, used only to authorize compensation on failure).
    @Column(name = "booked_by")
    private Long bookedBy;

    public Seat() {
    }

    public Seat(Long eventId, String seatNumber, String status) {
        this.eventId = eventId;
        this.seatNumber = seatNumber;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Long getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(Long lockedBy) {
        this.lockedBy = lockedBy;
    }

    public Long getBookedBy() {
        return bookedBy;
    }

    public void setBookedBy(Long bookedBy) {
        this.bookedBy = bookedBy;
    }

    // Computed for the API response only (not a column). Lets the frontend run
    // its countdown without depending on the browser clock or time zone.
    public Long getLockSecondsRemaining() {

        if (!"LOCKED".equals(status) || lockedUntil == null) {
            return null;
        }

        long seconds = java.time.Duration
                .between(LocalDateTime.now(), lockedUntil)
                .getSeconds();

        return seconds > 0 ? seconds : 0L;
    }
}
