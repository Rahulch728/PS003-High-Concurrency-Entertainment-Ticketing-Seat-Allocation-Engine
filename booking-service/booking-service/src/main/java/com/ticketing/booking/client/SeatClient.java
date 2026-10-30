package com.ticketing.booking.client;

import com.ticketing.booking.exception.BookingException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SeatClient {

    private static final Pattern MESSAGE_PATTERN =
            Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");

    private final RestTemplate restTemplate;

    public SeatClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public SeatResponse bookSeat(Long eventId, String seatNumber, Long userId) {

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));

        try {

            return restTemplate
                    .exchange(
                            "http://SEAT-SERVICE/seats/book/{eventId}/{seatNumber}",
                            HttpMethod.PUT,
                            new HttpEntity<>(headers),
                            SeatResponse.class,
                            eventId,
                            seatNumber
                    )
                    .getBody();

        } catch (HttpStatusCodeException e) {

            // Pass the seat service's status (404 / 409) and message on
            // to the caller instead of turning everything into a 500.
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());

            throw new BookingException(
                    status != null ? status : HttpStatus.BAD_GATEWAY,
                    extractMessage(e)
            );

        } catch (RuntimeException e) {

            throw new BookingException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Seat service is unavailable. Please try again"
            );
        }
    }

    private String extractMessage(HttpStatusCodeException e) {

        Matcher matcher =
                MESSAGE_PATTERN.matcher(e.getResponseBodyAsString());

        return matcher.find()
                ? matcher.group(1)
                : "Seat booking failed";
    }

    // Best-effort compensation: tells Seat Service to revert a seat it just
    // marked BOOKED, because the booking record could not be saved. Logged
    // but never thrown, since the booking failure is already the error that
    // matters to the caller.
    public boolean compensate(Long eventId, String seatNumber, Long userId) {

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));

        try {
            restTemplate.exchange(
                    "http://SEAT-SERVICE/seats/compensate/{eventId}/{seatNumber}",
                    HttpMethod.PUT,
                    new HttpEntity<>(headers),
                    Void.class,
                    eventId,
                    seatNumber
            );
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static class SeatResponse {

        private Long id;
        private Long eventId;
        private String seatNumber;
        private String status;

        public SeatResponse() {
        }

        public Long getId() {
            return id;
        }

        public Long getEventId() {
            return eventId;
        }

        public String getSeatNumber() {
            return seatNumber;
        }

        public String getStatus() {
            return status;
        }
    }
}
