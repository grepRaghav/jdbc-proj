package dev.showbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

public final class BookingServer {
    private final int port;
    private final BookingService service;
    private final ObjectMapper json = new ObjectMapper();

    public BookingServer(int port, Database database) {
        this.port = port;
        this.service = new BookingService(database);
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/events", exchange -> handle(exchange, "events"));
        server.createContext("/api/bookings", exchange -> handle(exchange, "bookings"));
        server.createContext("/", this::serveFrontend);
        server.setExecutor(null);
        server.start();
    }

    private void handle(HttpExchange exchange, String route) throws IOException {
        try (exchange) {
            try {
                addCors(exchange);
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                if ("events".equals(route) && "GET".equals(exchange.getRequestMethod())) {
                    sendJson(exchange, 200, service.listEvents());
                    return;
                }
                if ("bookings".equals(route) && "GET".equals(exchange.getRequestMethod())) {
                    sendJson(exchange, 200, service.listBookings());
                    return;
                }
                if ("bookings".equals(route) && "POST".equals(exchange.getRequestMethod())) {
                    BookingService.BookingRequest request = json.readValue(exchange.getRequestBody(), BookingService.BookingRequest.class);
                    sendJson(exchange, 201, service.createBooking(request));
                    return;
                }
                sendJson(exchange, 405, new ApiError("Method not allowed."));
            } catch (IllegalArgumentException error) {
                sendJson(exchange, 400, new ApiError(error.getMessage()));
            } catch (SQLException error) {
                sendJson(exchange, 500, new ApiError("Booking service is temporarily unavailable."));
            } catch (Exception error) {
                sendJson(exchange, 400, new ApiError("Please check the booking details and try again."));
            }
        }
    }

    private void serveFrontend(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            byte[] page;
            try (var input = BookingServer.class.getResourceAsStream("/index.html")) {
                if (input == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                page = input.readAllBytes();
            }
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, page.length);
            exchange.getResponseBody().write(page);
        }
    }

    private void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] response = json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
    }

    private record ApiError(String error) { }
}