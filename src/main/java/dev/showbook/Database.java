package dev.showbook;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {
    private final String url;

    public Database(String path) {
        this.url = "jdbc:sqlite:" + path;
    }

    public Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    public void initialize() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS venues (
                        id INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        city TEXT NOT NULL,
                        address TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS events (
                        id INTEGER PRIMARY KEY,
                        venue_id INTEGER NOT NULL REFERENCES venues(id),
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        event_date TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS customers (
                        id INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        email TEXT NOT NULL UNIQUE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ticket_types (
                        id INTEGER PRIMARY KEY,
                        event_id INTEGER NOT NULL REFERENCES events(id),
                        name TEXT NOT NULL,
                        price REAL NOT NULL CHECK(price >= 0),
                        capacity INTEGER NOT NULL CHECK(capacity > 0)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS bookings (
                        id INTEGER PRIMARY KEY,
                        customer_id INTEGER NOT NULL REFERENCES customers(id),
                        event_id INTEGER NOT NULL REFERENCES events(id),
                        booked_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        status TEXT NOT NULL DEFAULT 'CONFIRMED'
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS booking_tickets (
                        id INTEGER PRIMARY KEY,
                        booking_id INTEGER NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
                        ticket_type_id INTEGER NOT NULL REFERENCES ticket_types(id),
                        quantity INTEGER NOT NULL CHECK(quantity > 0),
                        unit_price REAL NOT NULL CHECK(unit_price >= 0)
                    )
                    """);
            seed(statement);
        }
    }

    private void seed(Statement statement) throws SQLException {
        statement.executeUpdate("INSERT OR IGNORE INTO venues(id, name, city, address) VALUES " +
                "(1, 'The Glasshouse', 'London', '18 River Street'), " +
                "(2, 'North Hall', 'Manchester', '42 Cotton Lane'), " +
                "(3, 'Civic Theatre', 'Bristol', '7 Beacon Road')");
        statement.executeUpdate("INSERT OR IGNORE INTO events(id, venue_id, title, description, event_date) VALUES " +
                "(1, 1, 'Neon Nights Live', 'An evening of bright synths and live electronic sets.', '2026-11-14T19:30:00'), " +
                "(2, 2, 'Laugh Track', 'A sharp stand-up showcase featuring new voices.', '2026-11-21T20:00:00'), " +
                "(3, 3, 'Strings After Dark', 'A candlelit chamber concert with a modern edge.', '2026-12-05T19:00:00')");
        statement.executeUpdate("INSERT OR IGNORE INTO ticket_types(id, event_id, name, price, capacity) VALUES " +
                "(1, 1, 'Standard', 24.00, 120), (2, 1, 'Front row', 38.00, 30), " +
                "(3, 2, 'Standard', 18.00, 100), (4, 2, 'Premium', 29.00, 25), " +
                "(5, 3, 'Standard', 22.00, 80), (6, 3, 'Premium', 34.00, 20)");
    }
}