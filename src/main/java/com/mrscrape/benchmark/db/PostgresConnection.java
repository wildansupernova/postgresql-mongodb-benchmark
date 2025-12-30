package com.mrscrape.benchmark.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.Statement;

public class PostgresConnection {
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    private final String connectionString;
    private HikariDataSource dataSource;

    public PostgresConnection(String connectionString) {
        this.connectionString = connectionString;
    }

    public void connect() throws Exception {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                // Configure HikariCP with settings equivalent to MongoDB's default connection pool
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl(connectionString);
                config.setDriverClassName("org.postgresql.Driver");

                // Match MongoDB's default connection pool settings but scale for high concurrency
                config.setMaximumPoolSize(300);        // Match PostgreSQL max_connections=300
                config.setMinimumIdle(20);             // Keep more idle connections ready
                config.setMaxLifetime(300000);         // 5 minutes (MongoDB default is unlimited)
                config.setIdleTimeout(60000);          // 1 minute (MongoDB default is unlimited)
                config.setConnectionTimeout(30000);    // 30 seconds (reduced from 2 minutes for faster failure)

                // Additional HikariCP optimizations
                config.setPoolName("BenchmarkPool");
                config.setConnectionTestQuery("SELECT 1");
                config.setValidationTimeout(5000);     // 5 seconds
                config.setLeakDetectionThreshold(60000); // 1 minute

                dataSource = new HikariDataSource(config);

                // Test the connection
                try (Connection testConn = dataSource.getConnection();
                     Statement stmt = testConn.createStatement()) {
                    stmt.execute("SELECT 1");
                }

                System.out.println("Connected to PostgreSQL with HikariCP connection pool successfully");
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

    public Connection getConnection() throws Exception {
        if (dataSource == null) {
            throw new IllegalStateException("Connection pool not initialized. Call connect() first.");
        }
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
            System.out.println("PostgreSQL connection pool closed");
        }
    }

    // Note: dropDatabase and createDatabase methods removed as they require
    // connecting to a different database (usually 'postgres') which is not
    // supported with the current connection pool configuration focused on benchmark_db
}
