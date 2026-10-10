# ShowBook

A small event-booking demo with a Java HTTP backend, SQLite database, and browser frontend. The backend uses JDBC through the Xerial SQLite JDBC driver. JDBC is the Java database interface; ODBC is a separate interface and the historic JDBC-ODBC bridge was removed from modern Java, so this project uses a direct JDBC driver instead.

## Requirements

- JDK 17 or newer
- Maven 3.8 or newer

## Run

```sh
mvn test
mvn exec:java
```

Open <http://localhost:8080>. The app creates `showbook.db` in the current directory and inserts sample venues, events, and ticket types the first time it starts. Override the defaults with `PORT` and `DB_PATH` environment variables.

## Relations

- `venues` has many `events`.
- `events` has many `ticket_types` and many `bookings`.
- `customers` has many `bookings`.
- `bookings` has many `booking_tickets`.
- `ticket_types` has many `booking_tickets`.

The `booking_tickets` relation records the quantity and the price at booking time. Foreign keys and ticket capacity checks are enabled.

## API

- `GET /api/events` lists events with ticket prices and remaining capacity.
- `POST /api/bookings` accepts `{ "name": "Alex Example", "email": "alex@example.com", "ticketTypeId": 1, "quantity": 2 }`.
- `GET /api/bookings` lists the latest 20 bookings.