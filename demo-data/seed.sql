-- Demo data for the ticketing system (MySQL 8).
--
-- Run AFTER all services have started once (Hibernate creates the tables and
-- the seats.locked_by / seats.booked_by columns when Seat Service boots).
--
-- Safe to re-run: it only INSERTs new rows, it never drops or deletes
-- anything, so it will not touch unrelated data in these databases.
--
--   mysql -u root -p < demo-data/seed.sql
--
-- Creates two events and 16 AVAILABLE seats (rows A-B, seats 1-8) for each.

USE ticketing_event;

INSERT INTO events (name, description, venue, event_date, total_seats) VALUES
  ('Indie Night Live', 'An evening of live indie bands with local and touring artists.', 'Riverside Hall', '2026-11-14 19:30:00', 16);
SET @indie_event = LAST_INSERT_ID();

INSERT INTO events (name, description, venue, event_date, total_seats) VALUES
  ('Open Air Cinema', 'A classic film screening under the stars. Bring a blanket.', 'City Park Lawn', '2026-12-05 20:00:00', 16);
SET @cinema_event = LAST_INSERT_ID();

USE ticketing_seat;

INSERT INTO seats (event_id, seat_number, status, locked_until, locked_by, booked_by)
SELECT e.event_id, CONCAT(r.row_letter, n.num), 'AVAILABLE', NULL, NULL, NULL
FROM (SELECT @indie_event AS event_id UNION ALL SELECT @cinema_event) e
CROSS JOIN (SELECT 'A' AS row_letter UNION ALL SELECT 'B') r
CROSS JOIN (SELECT 1 AS num UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8) n;

SELECT @indie_event AS indie_event_id, @cinema_event AS cinema_event_id;
