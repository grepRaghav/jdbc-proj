package dev.showbook;

public final class App {
    private App() {
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String databasePath = System.getenv().getOrDefault("DB_PATH", "showbook.db");
        Database database = new Database(databasePath);
        database.initialize();
        new BookingServer(port, database).start();
        System.out.println("ShowBook is running at http://localhost:" + port);
    }
}