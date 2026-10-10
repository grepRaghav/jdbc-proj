package dev.showbook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void listsSeededEventsAndTicketChoices() throws SQLException {
        BookingService service = service();

        assertEquals(3, service.listEvents().size());
        assertEquals(2, service.listEvents().get(0).tickets().size());
    }

    @Test
    void createsBookingAndShowsItInHistory() throws SQLException {
        BookingService service = service();

        BookingService.BookingReceipt receipt = service.createBooking(
                new BookingService.BookingRequest("Alex Example", "alex@example.com", 1, 2));

        assertEquals(48.0, receipt.total());
        assertEquals(1, service.listBookings().size());
        assertEquals(118, service.listEvents().get(0).tickets().get(0).available());
    }

    @Test
    void rejectsInvalidQuantity() throws SQLException {
        BookingService service = service();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.createBooking(
                new BookingService.BookingRequest("Alex Example", "alex@example.com", 1, 0)));

        assertTrue(error.getMessage().contains("quantity"));
        assertTrue(service.listBookings().isEmpty());
    }

    private BookingService service() throws SQLException {
        Database database = new Database(tempDir.resolve("test.db").toString());
        database.initialize();
        return new BookingService(database);
    }
}