package dev.showbook;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class BookingService {
    private final Database database;

    public BookingService(Database database) {
        this.database = database;
    }

    public List<EventView> listEvents() throws SQLException {
        String sql = """
                SELECT e.id, e.title, e.description, e.event_date, v.name AS venue, v.city,
                       t.id AS ticket_id, t.name AS ticket_name, t.price, t.capacity,
                       COALESCE(SUM(bt.quantity), 0) AS sold
                FROM events e
                JOIN venues v ON v.id = e.venue_id
                JOIN ticket_types t ON t.event_id = e.id
                LEFT JOIN booking_tickets bt ON bt.ticket_type_id = t.id
                GROUP BY e.id, t.id
                ORDER BY e.event_date
                """;
        List<EventView> events = new ArrayList<>();
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                int eventId = rows.getInt("id");
                EventView event = events.isEmpty() ? null : events.get(events.size() - 1);
                if (event == null || event.id() != eventId) {
                    event = new EventView(eventId, rows.getString("title"), rows.getString("description"),
                            rows.getString("event_date"), rows.getString("venue"), rows.getString("city"), new ArrayList<>());
                    events.add(event);
                }
                event.tickets().add(new TicketView(rows.getInt("ticket_id"), rows.getString("ticket_name"),
                        rows.getDouble("price"), Math.max(0, rows.getInt("capacity") - rows.getInt("sold"))));
            }
        }
        return events;
    }

    public synchronized BookingReceipt createBooking(BookingRequest request) throws SQLException {
        if (request.name() == null || request.name().isBlank() || request.email() == null ||
                !request.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || request.quantity() < 1 || request.quantity() > 10) {
            throw new IllegalArgumentException("Enter a name, valid email, and quantity from 1 to 10.");
        }

        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                double price;
                int eventId;
                int available;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT t.price, t.event_id, t.capacity - COALESCE(SUM(bt.quantity), 0) AS available
                        FROM ticket_types t
                        LEFT JOIN booking_tickets bt ON bt.ticket_type_id = t.id
                        WHERE t.id = ?
                        GROUP BY t.id
                        """)) {
                    statement.setInt(1, request.ticketTypeId());
                    try (ResultSet row = statement.executeQuery()) {
                        if (!row.next()) {
                            throw new IllegalArgumentException("That ticket type does not exist.");
                        }
                        price = row.getDouble("price");
                        eventId = row.getInt("event_id");
                        available = row.getInt("available");
                    }
                }
                if (request.quantity() > available) {
                    throw new IllegalArgumentException("Only " + available + " ticket(s) remain.");
                }

                int customerId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO customers(name, email) VALUES (?, ?)
                        ON CONFLICT(email) DO UPDATE SET name = excluded.name
                        """)) {
                    statement.setString(1, request.name().trim());
                    statement.setString(2, request.email().trim().toLowerCase());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM customers WHERE email = ?")) {
                    statement.setString(1, request.email().trim().toLowerCase());
                    try (ResultSet row = statement.executeQuery()) {
                        row.next();
                        customerId = row.getInt("id");
                    }
                }

                int bookingId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO bookings(customer_id, event_id) VALUES (?, ?)", PreparedStatement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, customerId);
                    statement.setInt(2, eventId);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        keys.next();
                        bookingId = keys.getInt(1);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO booking_tickets(booking_id, ticket_type_id, quantity, unit_price) VALUES (?, ?, ?, ?)")) {
                    statement.setInt(1, bookingId);
                    statement.setInt(2, request.ticketTypeId());
                    statement.setInt(3, request.quantity());
                    statement.setDouble(4, price);
                    statement.executeUpdate();
                }
                connection.commit();
                return new BookingReceipt(bookingId, request.quantity(), price * request.quantity());
            } catch (Exception error) {
                connection.rollback();
                if (error instanceof SQLException sqlException) {
                    throw sqlException;
                }
                if (error instanceof IllegalArgumentException illegalArgumentException) {
                    throw illegalArgumentException;
                }
                throw new SQLException("Could not create booking.", error);
            }
        }
    }

    public List<BookingView> listBookings() throws SQLException {
        String sql = """
                SELECT b.id, c.name AS customer, e.title AS event, t.name AS ticket,
                       bt.quantity, bt.unit_price, b.booked_at
                FROM bookings b
                JOIN customers c ON c.id = b.customer_id
                JOIN events e ON e.id = b.event_id
                JOIN booking_tickets bt ON bt.booking_id = b.id
                JOIN ticket_types t ON t.id = bt.ticket_type_id
                ORDER BY b.id DESC LIMIT 20
                """;
        List<BookingView> bookings = new ArrayList<>();
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                bookings.add(new BookingView(rows.getInt("id"), rows.getString("customer"), rows.getString("event"),
                        rows.getString("ticket"), rows.getInt("quantity"), rows.getDouble("unit_price") * rows.getInt("quantity"),
                        rows.getString("booked_at")));
            }
        }
        return bookings;
    }

    public record TicketView(int id, String name, double price, int available) { }
    public record EventView(int id, String title, String description, String date, String venue, String city,
                            List<TicketView> tickets) { }
    public record BookingRequest(String name, String email, int ticketTypeId, int quantity) { }
    public record BookingReceipt(int bookingId, int quantity, double total) { }
    public record BookingView(int id, String customer, String event, String ticket, int quantity, double total,
                              String bookedAt) { }
}