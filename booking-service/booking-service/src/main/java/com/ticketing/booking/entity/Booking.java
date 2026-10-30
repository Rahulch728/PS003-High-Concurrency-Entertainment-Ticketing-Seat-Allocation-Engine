package com.ticketing.booking.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private Long userId;

    @NotNull
    @Column(nullable = false)
    private Long eventId;

    @NotNull
    @Column(nullable = false)
    private Long seatId;

    @NotNull
    @Column(nullable = false)
    private String seatNumber;

    @NotNull
    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime bookingTime;

    public Booking() {
    }

    public Booking(Long userId, Long eventId, Long seatId,
                   String seatNumber, String status,
                   LocalDateTime bookingTime) {
        this.userId = userId;
        this.eventId = eventId;
        this.seatId = seatId;
        this.seatNumber = seatNumber;
        this.status = status;
        this.bookingTime = bookingTime;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getSeatId() {
        return seatId;
    }

    public void setSeatId(Long seatId) {
        this.seatId = seatId;
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

    public LocalDateTime getBookingTime() {
        return bookingTime;
    }

    public void setBookingTime(LocalDateTime bookingTime) {
        this.bookingTime = bookingTime;
    }
}