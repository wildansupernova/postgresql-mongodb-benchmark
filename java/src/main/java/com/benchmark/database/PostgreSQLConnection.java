package com.benchmark.database;

import com.benchmark.config.BenchmarkConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public class PostgreSQLConnection implements AutoCloseable {
    private final HikariDataSource dataSource;

    public PostgreSQLConnection(BenchmarkConfig config) {
        String host = config.getPostgresHost();
        String database = config.getPostgresDatabase();
        String user = config.getPostgresUser();
        String password = config.getPostgresPassword();

        Map<String, Object> poolConfig = config.getConnectionPoolConfig();
        int minSize = (Integer) poolConfig.get("min_size");
        int maxSize = (Integer) poolConfig.get("max_size");
        int connectionTimeout = (Integer) poolConfig.get("connection_timeout_ms");
        int idleTimeout = (Integer) poolConfig.get("idle_timeout_ms");

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:postgresql://" + host + "/" + database);
        hikariConfig.setUsername(user);
        hikariConfig.setPassword(password);
        hikariConfig.setMinimumIdle(minSize);
        hikariConfig.setMaximumPoolSize(maxSize);
        hikariConfig.setConnectionTimeout(connectionTimeout);
        hikariConfig.setIdleTimeout(idleTimeout);
        hikariConfig.setAutoCommit(false);

        this.dataSource = new HikariDataSource(hikariConfig);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
