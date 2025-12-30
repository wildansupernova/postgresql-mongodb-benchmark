package com.mrscrape.benchmark.db;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

public class MongoConnection {
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    private final String connectionString;
    private MongoClient mongoClient;
    private MongoDatabase database;

    public MongoConnection(String connectionString) {
        this.connectionString = connectionString;
    }

    public void connect() throws Exception {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                // Configure MongoDB connection pool for high concurrency
                MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(connectionString))
                    .applyToConnectionPoolSettings(builder ->
                        builder.maxSize(300)           // Match PostgreSQL max_connections=300 for fair comparison
                            .minSize(20)                // Keep more connections ready
                            .maxWaitTime(30, java.util.concurrent.TimeUnit.SECONDS)  // 30 seconds timeout
                            .maxConnectionIdleTime(60, java.util.concurrent.TimeUnit.SECONDS)  // 1 minute
                            .maxConnectionLifeTime(5, java.util.concurrent.TimeUnit.MINUTES)   // 5 minutes
                    )
                    .build();

                mongoClient = MongoClients.create(settings);
                mongoClient.getDatabase("admin").runCommand(new org.bson.Document("ping", 1));
                database = mongoClient.getDatabase("benchmark_db");
                System.out.println("Connected to MongoDB with configured connection pool successfully");
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

    public MongoDatabase getDatabase() {
        return database;
    }

    public MongoClient getClient() {
        return mongoClient;
    }

    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            System.out.println("MongoDB connection closed");
        }
    }

    public void dropDatabase() {
        if (database != null) {
            database.drop();
            System.out.println("Database dropped");
        }
    }
}
