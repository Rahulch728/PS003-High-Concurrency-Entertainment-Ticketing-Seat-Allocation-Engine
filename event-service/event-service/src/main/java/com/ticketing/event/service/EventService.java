package com.ticketing.event.service;

import com.ticketing.event.entity.Event;
import com.ticketing.event.exception.EventNotFoundException;
import com.ticketing.event.repository.EventRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public Event createEvent(Event event) {
        return eventRepository.save(event);
    }

    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    public Event getEventById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
    }

    public Event updateEvent(Long id, Event event) {
        Event existingEvent = getEventById(id);

        existingEvent.setName(event.getName());
        existingEvent.setDescription(event.getDescription());
        existingEvent.setVenue(event.getVenue());
        existingEvent.setEventDate(event.getEventDate());
        existingEvent.setTotalSeats(event.getTotalSeats());

        return eventRepository.save(existingEvent);
    }

    public void deleteEvent(Long id) {
        Event event = getEventById(id);
        eventRepository.delete(event);
    }
}