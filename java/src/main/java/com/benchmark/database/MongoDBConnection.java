package com.benchmark.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadConcern;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.benchmark.config.BenchmarkConfig;

public class MongoDBConnection implements AutoCloseable {
    private final MongoClient mongoClient;
    private final MongoDatabase database;

    public MongoDBConnection(BenchmarkConfig config) {
        String uri = config.getMongoUri();
        String databaseName = config.getMongoDatabase();

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .writeConcern(WriteConcern.MAJORITY)
                .readConcern(ReadConcern.MAJORITY)
                .build();

        this.mongoClient = MongoClients.create(settings);
        this.database = mongoClient.getDatabase(databaseName);
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
        }
    }
}
