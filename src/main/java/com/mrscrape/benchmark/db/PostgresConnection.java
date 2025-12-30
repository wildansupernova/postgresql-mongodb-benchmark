package com.mrscrape.benchmark.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class PostgresConnection {
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    private final String connectionString;
    private Connection connection;

    public PostgresConnection(String connectionString) {
        this.connectionString = connectionString;
    }

    public void connect() throws Exception {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                connection = DriverManager.getConnection(connectionString);
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("SELECT 1");
                }
                System.out.println("Connected to PostgreSQL successfully");
                return;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES - 1) {
                    long backoffMs = INITIAL_BACKOFF_MS * (long) Math.pow(2, attempt);
                    long jitter = (long) (Math.random() * backoffMs * 0.1);
                    Thread.sleep(backoffMs + jitter);
                } else {
                    throw e;
                }
            }
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("PostgreSQL connection closed");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void dropDatabase(String databaseName) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP DATABASE IF EXISTS " + databaseName + " WITH (FORCE);");
            System.out.println("Database dropped: " + databaseName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void createDatabase(String databaseName) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE DATABASE " + databaseName);
            System.out.println("Database created: " + databaseName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
