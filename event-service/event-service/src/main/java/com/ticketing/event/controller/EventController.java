package com.ticketing.event.controller;

import com.ticketing.event.entity.Event;
import com.ticketing.event.exception.ForbiddenException;
import com.ticketing.event.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/events")
@CrossOrigin
public class EventController {

    // The API Gateway validates the JWT and sends the caller's role in this
    // header. Only an ADMIN may create, update or delete events.
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Event createEvent(
            @RequestHeader(value = USER_ROLE_HEADER, required = false) String role,
            @Valid @RequestBody Event event) {

        requireAdmin(role);
        return eventService.createEvent(event);
    }

    @GetMapping
    public List<Event> getAllEvents() {
        return eventService.getAllEvents();
    }

    @GetMapping("/{id}")
    public Event getEventById(@PathVariable Long id) {
        return eventService.getEventById(id);
    }

    @PutMapping("/{id}")
    public Event updateEvent(
            @RequestHeader(value = USER_ROLE_HEADER, required = false) String role,
            @PathVariable Long id,
            @Valid @RequestBody Event event) {

        requireAdmin(role);
        return eventService.updateEvent(id, event);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(
            @RequestHeader(value = USER_ROLE_HEADER, required = false) String role,
            @PathVariable Long id) {

        requireAdmin(role);
        eventService.deleteEvent(id);
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException(
                    "Only an administrator can manage events");
        }
    }
}
