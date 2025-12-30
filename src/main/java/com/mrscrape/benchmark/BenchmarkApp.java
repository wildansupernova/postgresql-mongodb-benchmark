package com.mrscrape.benchmark;

import com.mrscrape.benchmark.config.BenchmarkConfig;
import com.mrscrape.benchmark.concurrency.VirtualThreadExecutor;
import com.mrscrape.benchmark.db.DatabaseOperations;
import com.mrscrape.benchmark.db.MongoConnection;
import com.mrscrape.benchmark.db.PostgresConnection;
import com.mrscrape.benchmark.db.scenario1.MongoEmbeddedOps;
import com.mrscrape.benchmark.db.scenario1.PostgresJsonbOps;
import com.mrscrape.benchmark.db.scenario2.MongoMultiDocOps;
import com.mrscrape.benchmark.db.scenario2.PostgresMultiTableOps;
import com.mrscrape.benchmark.metrics.CsvOutput;
import com.mrscrape.benchmark.metrics.MetricsCollector;
import com.mrscrape.benchmark.model.Item;
import com.mrscrape.benchmark.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BenchmarkApp implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(BenchmarkApp.class);
    
    private BenchmarkConfig config;

    public BenchmarkApp(BenchmarkConfig config) {
        this.config = config;
    }

    @Override
    public void run() {
        try {
            config.validate();
            
            if ("measurement".equalsIgnoreCase(config.getMode())) {
                runMeasurement();
            } else if ("aggregation".equalsIgnoreCase(config.getMode())) {
                runAggregation();
            }
        } catch (Exception e) {
            logger.error("Benchmark failed", e);
            System.exit(1);
        }
    }

    private void runMeasurement() throws Exception {
        logger.info("Starting benchmark in MEASUREMENT mode");
        logger.info("Scenario: {}, Database: {}", config.getScenario(), config.getDatabase());
        
        DatabaseOperations operations = createDatabaseOperations();
        VirtualThreadExecutor executor = new VirtualThreadExecutor(config.getConcurrency());
        MetricsCollector collector = new MetricsCollector();
        
        try {
            operations.setup();
            logger.info("Database schema setup completed");
            
            runInserts(executor, operations, collector);
            executor.waitForCompletion();
            collector.endTime("insert");
            logger.info("INSERT operations completed");
            
            runUpdateModify(executor, operations, collector);
            executor.waitForCompletion();
            collector.endTime("update-modify");
            logger.info("UPDATE-MODIFY operations completed");
            
            runUpdateAdd(executor, operations, collector);
            executor.waitForCompletion();
            collector.endTime("update-add");
            logger.info("UPDATE-ADD operations completed");
            
            runQueries(executor, operations, collector);
            executor.waitForCompletion();
            collector.endTime("query");
            logger.info("QUERY operations completed");
            
            runDeletes(executor, operations, collector);
            executor.waitForCompletion();
            collector.endTime("delete");
            logger.info("DELETE operations completed");
            
            logger.info("All operations completed");
            
            if (executor.getExceptionCount() > 0) {
                logger.error("Benchmark encountered {} exceptions in virtual threads:", executor.getExceptionCount());
                for (Exception ex : executor.getExceptions()) {
                    logger.error("  - {}", ex.getMessage(), ex);
                }
                throw new Exception("Benchmark failed due to " + executor.getExceptionCount() + " exceptions in virtual threads");
            }
            
            if (collector.getFailureCount() > 0) {
                logger.error("Benchmark completed with {} operation failures:", collector.getFailureCount());
                for (String failure : collector.getFailures()) {
                    logger.error("  - {}", failure);
                }
                throw new Exception("Benchmark failed due to " + collector.getFailureCount() + " operation failures");
            }
            
            CsvOutput.writeMeasurementResults(config.getOutputFile(), collector);
            logger.info("Results written to: {}", config.getOutputFile());
            
        } finally {
            executor.shutdown();
            operations.teardown();
            logger.info("Database cleanup completed");
        }
    }

    private void runInserts(VirtualThreadExecutor executor, DatabaseOperations operations, 
            MetricsCollector collector) throws Exception {
        logger.info("Starting INSERT operations: {}", config.getInsertCount());
        collector.startTime("insert");
        
        for (int i = 0; i < config.getInsertCount(); i++) {
            final int orderId = i;
            executor.execute(() -> {
                try {
                    Order order = generateOrder(String.valueOf(orderId));
                    long startNs = System.nanoTime();
                    operations.insert(order);
                    double latencyMs = (System.nanoTime() - startNs) / 1_000_000.0;
                    collector.recordLatency("insert", latencyMs);
                } catch (Exception e) {
                    logger.error("Insert failed for order {}", orderId, e);
                    collector.recordFailure("insert", e);
                }
            });
        }
    }

    private void runUpdateModify(VirtualThreadExecutor executor, DatabaseOperations operations, 
            MetricsCollector collector) throws Exception {
        logger.info("Starting UPDATE-MODIFY operations: {}", config.getUpdateModifyCount());
        collector.startTime("update-modify");
        
        int updateLimit = Math.min(config.getUpdateModifyCount(), config.getInsertCount());
        for (int i = 0; i < updateLimit; i++) {
            final int orderId = i;
            executor.execute(() -> {
                try {
                    long startNs = System.nanoTime();
                    operations.updateModify(String.valueOf(orderId));
                    double latencyMs = (System.nanoTime() - startNs) / 1_000_000.0;
                    collector.recordLatency("update-modify", latencyMs);
                } catch (Exception e) {
                    logger.error("Update-modify failed for order {}", orderId, e);
                    collector.recordFailure("update-modify", e);
                }
            });
        }
    }

    private void runUpdateAdd(VirtualThreadExecutor executor, DatabaseOperations operations, 
            MetricsCollector collector) throws Exception {
        logger.info("Starting UPDATE-ADD operations: {}", config.getUpdateAddCount());
        collector.startTime("update-add");
        
        int updateLimit = Math.min(config.getUpdateAddCount(), config.getInsertCount());
        for (int i = 0; i < updateLimit; i++) {
            final int orderId = i;
            executor.execute(() -> {
                try {
                    long startNs = System.nanoTime();
                    operations.updateAdd(String.valueOf(orderId));
                    double latencyMs = (System.nanoTime() - startNs) / 1_000_000.0;
                    collector.recordLatency("update-add", latencyMs);
                } catch (Exception e) {
                    logger.error("Update-add failed for order {}", orderId, e);
                    collector.recordFailure("update-add", e);
                }
            });
        }
    }

    private void runQueries(VirtualThreadExecutor executor, DatabaseOperations operations, 
            MetricsCollector collector) throws Exception {
        logger.info("Starting QUERY operations: {}", config.getQueryCount());
        collector.startTime("query");
        
        int queryLimit = Math.min(config.getQueryCount(), config.getInsertCount());
        for (int i = 0; i < queryLimit; i++) {
            final int orderId = i;
            executor.execute(() -> {
                try {
                    long startNs = System.nanoTime();
                    operations.query(String.valueOf(orderId));
                    double latencyMs = (System.nanoTime() - startNs) / 1_000_000.0;
                    collector.recordLatency("query", latencyMs);
                } catch (Exception e) {
                    logger.error("Query failed for order {}", orderId, e);
                    collector.recordFailure("query", e);
                }
            });
        }
    }

    private void runDeletes(VirtualThreadExecutor executor, DatabaseOperations operations, 
            MetricsCollector collector) throws Exception {
        logger.info("Starting DELETE operations: {}", config.getDeleteCount());
        collector.startTime("delete");
        
        int deleteLimit = Math.min(config.getDeleteCount(), config.getInsertCount());
        for (int i = 0; i < deleteLimit; i++) {
            final int orderId = i;
            executor.execute(() -> {
                try {
                    long startNs = System.nanoTime();
                    operations.delete(String.valueOf(orderId));
                    double latencyMs = (System.nanoTime() - startNs) / 1_000_000.0;
                    collector.recordLatency("delete", latencyMs);
                } catch (Exception e) {
                    logger.error("Delete failed", e);
                    collector.recordFailure("delete", e);
                }
            });
        }
    }

    private Order generateOrder(String orderId) {
        Order order = new Order(
                orderId,
                "customer_" + orderId,
                Instant.now(),
                "pending"
        );
        
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        for (int i = 0; i < 10; i++) {
            Item item = new Item(
                    orderId + "_item_" + i,
                    orderId,
                    "product_" + i,
                    "Product " + i,
                    (long)(rand.nextDouble() * 400) + 100,
                    rand.nextInt(10) + 1
            );
            order.addItem(item);
        }
        
        return order;
    }

    private DatabaseOperations createDatabaseOperations() throws Exception {
        int scenario = config.getScenario();
        String database = config.getDatabase();
        
        if ("mongodb".equalsIgnoreCase(database)) {
            MongoConnection mongoConnection = new MongoConnection(config.getConnectionString());
            mongoConnection.connect();
            
            if (scenario == 1) {
                return new MongoEmbeddedOps(mongoConnection);
            } else if (scenario == 2) {
                return new MongoMultiDocOps(mongoConnection);
            }
        } else if ("postgresql".equalsIgnoreCase(database)) {
            PostgresConnection postgresConnection = new PostgresConnection(config.getConnectionString());
            postgresConnection.connect();
            
            if (scenario == 1) {
                return new PostgresJsonbOps(postgresConnection);
            } else if (scenario == 2) {
                return new PostgresMultiTableOps(postgresConnection);
            }
        }
        
        throw new Exception("Invalid scenario or database: " + scenario + ", " + database);
    }

    private void runAggregation() throws Exception {
        logger.info("Starting benchmark in AGGREGATION mode");
        
        String[] inputFiles = config.getInputFiles().split(",");
        for (int i = 0; i < inputFiles.length; i++) {
            inputFiles[i] = inputFiles[i].trim();
        }
        
        logger.info("Aggregating {} input files", inputFiles.length);
        List<String> fileList = new ArrayList<>();
        for (String file : inputFiles) {
            if (Files.exists(Paths.get(file))) {
                fileList.add(file);
                logger.info("  - {}", file);
            } else {
                logger.warn("Input file not found: {}", file);
            }
        }
        
        if (fileList.isEmpty()) {
            throw new Exception("No valid input files found");
        }
        
        CsvOutput.writeAggregationResults(config.getOutputFile(), fileList, 
                CsvOutput.readMeasurementFiles(fileList));
        logger.info("Aggregated results written to: {}", config.getOutputFile());
    }

    public static void main(String[] args) {
        try {
            BenchmarkConfig config = new BenchmarkConfig();
            CommandLine cmd = new CommandLine(config);
            CommandLine.ParseResult result = cmd.parseArgs(args);
            
            if (cmd.isUsageHelpRequested()) {
                cmd.usage(System.out);
                System.exit(0);
            }
            if (cmd.isVersionHelpRequested()) {
                cmd.printVersionHelp(System.out);
                System.exit(0);
            }
            
            BenchmarkApp app = new BenchmarkApp(config);
            app.run();
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
