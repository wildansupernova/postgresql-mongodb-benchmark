package com.benchmark;

import com.benchmark.config.BenchmarkConfig;
import com.benchmark.database.MongoDBConnection;
import com.benchmark.database.PostgreSQLConnection;
import com.benchmark.metrics.MetricsCollector;
import com.benchmark.metrics.MetricsResult;
import com.benchmark.models.Item;
import com.benchmark.models.Order;
import com.benchmark.results.BenchmarkResults;
import com.benchmark.results.ResultsWriter;
import com.benchmark.scenarios.BenchmarkScenario;
import com.benchmark.scenarios.scenario1.MongoDBEmbeddedScenario;
import com.benchmark.scenarios.scenario1.PostgreSQLJsonbScenario;
import com.benchmark.scenarios.scenario2.PostgreSQLNormalizedScenario;
import com.benchmark.scenarios.scenario3.MongoDBMultiCollectionScenario;
import com.benchmark.testdata.DataGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BenchmarkRunner {
    private static final Logger logger = LoggerFactory.getLogger(BenchmarkRunner.class);
    
    private final BenchmarkConfig config;
    private final DataGenerator dataGenerator;

    public BenchmarkRunner() {
        this.config = new BenchmarkConfig();
        this.dataGenerator = new DataGenerator();
    }

    public static void main(String[] args) {
        BenchmarkRunner runner = new BenchmarkRunner();
        runner.run();
    }

    public void run() {
        logger.info("Starting MongoDB vs PostgreSQL Benchmark");
        
        List<String> scales = config.getScales();
        List<Integer> concurrencyLevels = config.getConcurrencyLevels();
        List<String> enabledScenarios = config.getEnabledScenarios();
        
        logger.info("Enabled scenarios: {}", enabledScenarios);
        
        for (String scale : scales) {
            for (Integer concurrency : concurrencyLevels) {
                for (String scenario : enabledScenarios) {
                    switch (scenario) {
                        case "scenario1":
                            logger.info("Running Scenario 1: Embedded vs JSONB - Scale: {}, Concurrency: {}", scale, concurrency);
                            runScenario1(scale, concurrency);
                            break;
                        case "scenario2":
                            logger.info("Running Scenario 2: Embedded vs Normalized - Scale: {}, Concurrency: {}", scale, concurrency);
                            runScenario2(scale, concurrency);
                            break;
                        case "scenario3":
                            logger.info("Running Scenario 3: Multi-Collection vs JSONB - Scale: {}, Concurrency: {}", scale, concurrency);
                            runScenario3(scale, concurrency);
                            break;
                        case "scenario4":
                            logger.info("Running Scenario 4: Multi-Collection vs Normalized - Scale: {}, Concurrency: {}", scale, concurrency);
                            runScenario4(scale, concurrency);
                            break;
                        default:
                            logger.warn("Unknown scenario: {}", scenario);
                    }
                }
            }
        }
        
        logger.info("Benchmark completed");
    }

    private void runScenario1(String scale, int concurrency) {
        Map<String, Object> scaleConfig = config.getScaleConfig(scale);
        int orderCount = (Integer) scaleConfig.get("order_count");
        int itemsMin = (Integer) scaleConfig.get("items_per_order_min");
        int itemsMax = (Integer) scaleConfig.get("items_per_order_max");
        double priceMin = ((Number) scaleConfig.get("unit_price_min")).doubleValue();
        double priceMax = ((Number) scaleConfig.get("unit_price_max")).doubleValue();
        int qtyMin = (Integer) scaleConfig.get("quantity_min");
        int qtyMax = (Integer) scaleConfig.get("quantity_max");

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String outputDir = String.format("results/scenario1-%s-c%d-%s", scale, concurrency, timestamp);
        
        try (MongoDBConnection mongoConn = new MongoDBConnection(config);
             PostgreSQLConnection pgConn = new PostgreSQLConnection(config)) {
            
            MongoDBEmbeddedScenario mongoScenario = new MongoDBEmbeddedScenario(mongoConn);
            PostgreSQLJsonbScenario pgScenario = new PostgreSQLJsonbScenario(pgConn);
            
            mongoScenario.setup();
            pgScenario.setup();
            
            BenchmarkResults results = new BenchmarkResults("scenario1", scale, concurrency);
            
            // Run ALL PostgreSQL operations first for fairness
            logger.info("Running PostgreSQL benchmarks...");
            logger.info("PostgreSQL: INSERT benchmark (seeding data)");
            List<UUID> pgOrderIds = new ArrayList<>();
            results.addResult("insert", "postgresql", runInsertBenchmark(pgScenario, orderCount, itemsMin, itemsMax, priceMin, priceMax, qtyMin, qtyMax, concurrency, pgOrderIds));
            
            logger.info("PostgreSQL: APPEND benchmark");
            results.addResult("append", "postgresql", runAppendBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: UPDATE benchmark");
            results.addResult("update", "postgresql", runUpdateBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: DELETE benchmark");
            results.addResult("delete", "postgresql", runDeleteBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: BATCH_INSERT benchmark");
            results.addResult("batch_insert", "postgresql", runBatchInsertBenchmark(pgScenario, orderCount, itemsMin, itemsMax, priceMin, priceMax, qtyMin, qtyMax, concurrency));
            
            logger.info("PostgreSQL: FETCH_ORDER benchmark");
            results.addResult("fetch_order", "postgresql", runFetchBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: FETCH_FILTERED benchmark");
            results.addResult("fetch_filtered", "postgresql", runFetchFilteredBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: COUNT benchmark");
            results.addResult("count", "postgresql", runCountBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: AGGREGATE benchmark");
            results.addResult("aggregate", "postgresql", runAggregateBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: BATCH_FETCH benchmark");
            results.addResult("batch_fetch", "postgresql", runBatchFetchBenchmark(pgScenario, pgOrderIds, concurrency));
            
            // Now run ALL MongoDB operations
            logger.info("Running MongoDB benchmarks...");
            logger.info("MongoDB: INSERT benchmark (seeding data)");
            List<UUID> mongoOrderIds = new ArrayList<>();
            results.addResult("insert", "mongodb", runInsertBenchmark(mongoScenario, orderCount, itemsMin, itemsMax, priceMin, priceMax, qtyMin, qtyMax, concurrency, mongoOrderIds));
            
            logger.info("MongoDB: APPEND benchmark");
            results.addResult("append", "mongodb", runAppendBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: UPDATE benchmark");
            results.addResult("update", "mongodb", runUpdateBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: DELETE benchmark");
            results.addResult("delete", "mongodb", runDeleteBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: BATCH_INSERT benchmark");
            results.addResult("batch_insert", "mongodb", runBatchInsertBenchmark(mongoScenario, orderCount, itemsMin, itemsMax, priceMin, priceMax, qtyMin, qtyMax, concurrency));
            
            logger.info("MongoDB: FETCH_ORDER benchmark");
            results.addResult("fetch_order", "mongodb", runFetchBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: FETCH_FILTERED benchmark");
            results.addResult("fetch_filtered", "mongodb", runFetchFilteredBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: COUNT benchmark");
            results.addResult("count", "mongodb", runCountBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: AGGREGATE benchmark");
            results.addResult("aggregate", "mongodb", runAggregateBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: BATCH_FETCH benchmark");
            results.addResult("batch_fetch", "mongodb", runBatchFetchBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("Writing results");
            ResultsWriter writer = new ResultsWriter(outputDir);
            writer.writeResults(results);
            
            mongoScenario.cleanup();
            pgScenario.cleanup();
            
        } catch (Exception e) {
            logger.error("Benchmark failed", e);
        }
    }

    private void runScenario2(String scale, int concurrency) {
        Map<String, Object> scaleConfig = config.getScaleConfig(scale);
        int orderCount = (Integer) scaleConfig.get("order_count");
        int itemsMin = (Integer) scaleConfig.get("items_per_order_min");
        int itemsMax = (Integer) scaleConfig.get("items_per_order_max");
        double priceMin = ((Number) scaleConfig.get("unit_price_min")).doubleValue();
        double priceMax = ((Number) scaleConfig.get("unit_price_max")).doubleValue();
        int qtyMin = (Integer) scaleConfig.get("quantity_min");
        int qtyMax = (Integer) scaleConfig.get("quantity_max");

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String outputDir = String.format("results/scenario2-%s-c%d-%s", scale, concurrency, timestamp);
        
        try (MongoDBConnection mongoConn = new MongoDBConnection(config);
             PostgreSQLConnection pgConn = new PostgreSQLConnection(config)) {
            
            com.benchmark.scenarios.scenario2.MongoDBEmbeddedScenario mongoScenario = 
                new com.benchmark.scenarios.scenario2.MongoDBEmbeddedScenario(mongoConn);
            PostgreSQLNormalizedScenario pgScenario = new PostgreSQLNormalizedScenario(pgConn);
            
            mongoScenario.setup();
            pgScenario.setup();
            
            BenchmarkResults results = new BenchmarkResults("scenario2", scale, concurrency);
            
            logger.info("Running PostgreSQL (Normalized) benchmarks...");
            logger.info("PostgreSQL: INSERT benchmark (seeding data)");
            List<UUID> pgOrderIds = new ArrayList<>();
            results.addResult("insert", "postgresql", runInsertBenchmark(pgScenario, orderCount, itemsMin, itemsMax, priceMin, priceMax, qtyMin, qtyMax, concurrency, pgOrderIds));
            
            logger.info("PostgreSQL: APPEND benchmark");
            results.addResult("append", "postgresql", runAppendBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: UPDATE benchmark");
            results.addResult("update", "postgresql", runUpdateBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: DELETE benchmark");
            results.addResult("delete", "postgresql", runDeleteBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: BATCH_INSERT benchmark");
            results.addResult("batch_insert", "postgresql", runBatchInsertBenchmark(pgScenario, orderCount, itemsMin, itemsMax, priceMin, priceMax, qtyMin, qtyMax, concurrency));
            
            logger.info("PostgreSQL: FETCH_ORDER benchmark");
            results.addResult("fetch_order", "postgresql", runFetchBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: FETCH_FILTERED benchmark");
            results.addResult("fetch_filtered", "postgresql", runFetchFilteredBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: COUNT benchmark");
            results.addResult("count", "postgresql", runCountBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: AGGREGATE benchmark");
            results.addResult("aggregate", "postgresql", runAggregateBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: BATCH_FETCH benchmark");
            results.addResult("batch_fetch", "postgresql", runBatchFetchBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("Running MongoDB (Embedded) benchmarks...");
            logger.info("MongoDB: INSERT benchmark (seeding data)");
            List<UUID> mongoOrderIds = new ArrayList<>();
            results.addResult("insert", "mongodb", runInsertBenchmark(mongoScenario, orderCount, itemsMin, itemsMax, priceMin, priceMax, qtyMin, qtyMax, concurrency, mongoOrderIds));
            
            logger.info("MongoDB: APPEND benchmark");
            results.addResult("append", "mongodb", runAppendBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: UPDATE benchmark");
            results.addResult("update", "mongodb", runUpdateBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: DELETE benchmark");
            results.addResult("delete", "mongodb", runDeleteBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: BATCH_INSERT benchmark");
            results.addResult("batch_insert", "mongodb", runBatchInsertBenchmark(mongoScenario, orderCount, itemsMin, itemsMax, priceMin, priceMax, qtyMin, qtyMax, concurrency));
            
            logger.info("MongoDB: FETCH_ORDER benchmark");
            results.addResult("fetch_order", "mongodb", runFetchBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: FETCH_FILTERED benchmark");
            results.addResult("fetch_filtered", "mongodb", runFetchFilteredBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: COUNT benchmark");
            results.addResult("count", "mongodb", runCountBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: AGGREGATE benchmark");
            results.addResult("aggregate", "mongodb", runAggregateBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: BATCH_FETCH benchmark");
            results.addResult("batch_fetch", "mongodb", runBatchFetchBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("Writing results");
            ResultsWriter writer = new ResultsWriter(outputDir);
            writer.writeResults(results);
            
            mongoScenario.cleanup();
            pgScenario.cleanup();
            
        } catch (Exception e) {
            logger.error("Scenario 2 benchmark failed", e);
        }
    }

    private MetricsResult runInsertBenchmark(BenchmarkScenario scenario, int orderCount, int itemsMin, int itemsMax, 
                                              double priceMin, double priceMax, int qtyMin, int qtyMax, 
                                              int concurrency, List<UUID> orderIds) {
        MetricsCollector metrics = new MetricsCollector();
        metrics.start();
        
        Semaphore semaphore = new Semaphore(concurrency);
        AtomicInteger[] completedCounts = new AtomicInteger[100];
        for (int i = 0; i < completedCounts.length; i++) {
            completedCounts[i] = new AtomicInteger(0);
        }
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < orderCount; i++) {
                // Generate order on-demand
                Order order = dataGenerator.generateOrder(itemsMin, itemsMax, priceMin, priceMax, qtyMin, qtyMax);
                orderIds.add(order.getId());

                
                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        long start = System.currentTimeMillis();
                        scenario.insert(order);
                        long duration = System.currentTimeMillis() - start;
                        metrics.recordLatency(duration);
                        
                        int randomIndex = ThreadLocalRandom.current().nextInt(100);
                        completedCounts[randomIndex].incrementAndGet();
                        
                        int totalCompleted = 0;
                        for (AtomicInteger count : completedCounts) {
                            totalCompleted += count.get();
                        }
                        
                        if (totalCompleted % 1000 == 0) {
                            logger.info("Inserted {} orders", totalCompleted);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        semaphore.release();
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException("Insert benchmark interrupted", e);
        }
        
        metrics.stop();
        return metrics.getResults();
    }

    private MetricsResult runFetchBenchmark(BenchmarkScenario scenario, List<UUID> orderIds, int concurrency) {
        MetricsCollector metrics = new MetricsCollector();
        metrics.start();
        
        int operationsCount = Math.min(config.getTotalOperations(), orderIds.size());
        Semaphore semaphore = new Semaphore(concurrency);
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < operationsCount; i++) {
                UUID orderId = orderIds.get(i % orderIds.size());
                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        long start = System.currentTimeMillis();
                        scenario.fetchOrder(orderId);
                        long duration = System.currentTimeMillis() - start;
                        metrics.recordLatency(duration);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        semaphore.release();
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException("Fetch benchmark interrupted", e);
        }
        
        metrics.stop();
        return metrics.getResults();
    }

    private MetricsResult runCountBenchmark(BenchmarkScenario scenario, List<UUID> orderIds, int concurrency) {
        MetricsCollector metrics = new MetricsCollector();
        metrics.start();
        
        int operationsCount = Math.min(config.getTotalOperations(), orderIds.size());
        Semaphore semaphore = new Semaphore(concurrency);
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < operationsCount; i++) {
                UUID orderId = orderIds.get(i % orderIds.size());
                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        long start = System.currentTimeMillis();
                        scenario.count(orderId);
                        long duration = System.currentTimeMillis() - start;
                        metrics.recordLatency(duration);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        semaphore.release();
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException("Count benchmark interrupted", e);
        }
        
        metrics.stop();
        return metrics.getResults();
    }

    private MetricsResult runAppendBenchmark(BenchmarkScenario scenario, List<UUID> orderIds, int concurrency) {
        MetricsCollector metrics = new MetricsCollector();
        metrics.start();
        
        int operationsCount = Math.min(config.getTotalOperations(), orderIds.size());
        Semaphore semaphore = new Semaphore(concurrency);
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < operationsCount; i++) {
                UUID orderId = orderIds.get(i % orderIds.size());
                Item newItem = dataGenerator.generateItem(orderId, 10.0, 100.0, 1, 5);
                List<Item> items = List.of(newItem);
                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        long start = System.currentTimeMillis();
                        scenario.append(orderId, items);
                        long duration = System.currentTimeMillis() - start;
                        metrics.recordLatency(duration);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        semaphore.release();
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException("Append benchmark interrupted", e);
        }
        
        metrics.stop();
        return metrics.getResults();
    }

    private MetricsResult runUpdateBenchmark(BenchmarkScenario scenario, List<UUID> orderIds, int concurrency) {
        MetricsCollector metrics = new MetricsCollector();
        metrics.start();
        
        int operationsCount = Math.min(config.getTotalOperations(), orderIds.size());
        Semaphore semaphore = new Semaphore(concurrency);
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < operationsCount; i++) {
                UUID orderId = orderIds.get(i % orderIds.size());
                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        long start = System.currentTimeMillis();
                        // Fetch order to get first item ID, then update it
                        Order order = scenario.fetchOrder(orderId);
                        if (order != null && !order.getItems().isEmpty()) {
                            Item firstItem = order.getItems().get(0);
                            Item updatedItem = dataGenerator.generateItem(orderId, 10.0, 100.0, 1, 10);
                            scenario.update(orderId, firstItem.getId(), updatedItem);
                        }
                        long duration = System.currentTimeMillis() - start;
                        metrics.recordLatency(duration);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        semaphore.release();
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException("Update benchmark interrupted", e);
        }
        
        metrics.stop();
        return metrics.getResults();
    }

    private MetricsResult runDeleteBenchmark(BenchmarkScenario scenario, List<UUID> orderIds, int concurrency) {
        MetricsCollector metrics = new MetricsCollector();
        metrics.start();
        
        int operationsCount = Math.min(config.getTotalOperations(), orderIds.size());
        Semaphore semaphore = new Semaphore(concurrency);
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < operationsCount; i++) {
                UUID orderId = orderIds.get(i % orderIds.size());
                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        long start = System.currentTimeMillis();
                        // Fetch order to get first item ID, then delete it
                        Order order = scenario.fetchOrder(orderId);
                        if (order != null && !order.getItems().isEmpty()) {
                            UUID itemId = order.getItems().get(0).getId();
                            scenario.delete(orderId, itemId);
                        }
                        long duration = System.currentTimeMillis() - start;
                        metrics.recordLatency(duration);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        semaphore.release();
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException("Delete benchmark interrupted", e);
        }
        
        metrics.stop();
        return metrics.getResults();
    }

    private MetricsResult runBatchInsertBenchmark(BenchmarkScenario scenario, int orderCount, int itemsMin, int itemsMax,
                                                   double priceMin, double priceMax, int qtyMin, int qtyMax, int concurrency) {
        MetricsCollector metrics = new MetricsCollector();
        metrics.start();
        
        int batchSize = 100;
        int batchCount = Math.min(10, orderCount / batchSize);
        Semaphore semaphore = new Semaphore(concurrency);
        AtomicInteger[] completedBatchCounts = new AtomicInteger[100];
        for (int i = 0; i < completedBatchCounts.length; i++) {
            completedBatchCounts[i] = new AtomicInteger(0);
        }
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < batchCount; i++) {
                // Generate batch on-demand
                List<Order> batch = new ArrayList<>();
                for (int j = 0; j < batchSize; j++) {
                    batch.add(dataGenerator.generateOrder(itemsMin, itemsMax, priceMin, priceMax, qtyMin, qtyMax));
                }
                
                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        long start = System.currentTimeMillis();
                        scenario.batchInsert(batch);
                        long duration = System.currentTimeMillis() - start;
                        metrics.recordLatency(duration);
                        
                        int randomIndex = ThreadLocalRandom.current().nextInt(100);
                        completedBatchCounts[randomIndex].incrementAndGet();
                        
                        int totalCompleted = 0;
                        for (AtomicInteger count : completedBatchCounts) {
                            totalCompleted += count.get();
                        }
                        
                        if (totalCompleted % 10 == 0) {
                            logger.info("Inserted {} batches ({} orders)", totalCompleted, totalCompleted * batchSize);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        semaphore.release();
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException("Batch insert benchmark interrupted", e);
        }
        
        metrics.stop();
        return metrics.getResults();
    }

    private MetricsResult runFetchFilteredBenchmark(BenchmarkScenario scenario, List<UUID> orderIds, int concurrency) {
        MetricsCollector metrics = new MetricsCollector();
        metrics.start();
        
        int operationsCount = Math.min(config.getTotalOperations(), orderIds.size());
        Semaphore semaphore = new Semaphore(concurrency);
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < operationsCount; i++) {
                UUID orderId = orderIds.get(i % orderIds.size());
                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        long start = System.currentTimeMillis();
                        scenario.fetchFiltered(orderId, "pending");
                        long duration = System.currentTimeMillis() - start;
                        metrics.recordLatency(duration);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        semaphore.release();
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException("Fetch filtered benchmark interrupted", e);
        }
        
        metrics.stop();
        return metrics.getResults();
    }

    private MetricsResult runAggregateBenchmark(BenchmarkScenario scenario, List<UUID> orderIds, int concurrency) {
        MetricsCollector metrics = new MetricsCollector();
        metrics.start();
        
        int operationsCount = Math.min(config.getTotalOperations(), orderIds.size());
        Semaphore semaphore = new Semaphore(concurrency);
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < operationsCount; i++) {
                UUID orderId = orderIds.get(i % orderIds.size());
                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        long start = System.currentTimeMillis();
                        scenario.aggregate(orderId);
                        long duration = System.currentTimeMillis() - start;
                        metrics.recordLatency(duration);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        semaphore.release();
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException("Aggregate benchmark interrupted", e);
        }
        
        metrics.stop();
        return metrics.getResults();
    }

    private MetricsResult runBatchFetchBenchmark(BenchmarkScenario scenario, List<UUID> orderIds, int concurrency) {
        MetricsCollector metrics = new MetricsCollector();
        metrics.start();
        
        int batchSize = 10;
        int batchCount = Math.min(100, orderIds.size() / batchSize);
        Semaphore semaphore = new Semaphore(concurrency);
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < batchCount; i++) {
                int startIdx = i * batchSize;
                int endIdx = Math.min(startIdx + batchSize, orderIds.size());
                List<UUID> batch = orderIds.subList(startIdx, endIdx);
                
                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        long start = System.currentTimeMillis();
                        scenario.batchFetch(batch);
                        long duration = System.currentTimeMillis() - start;
                        metrics.recordLatency(duration);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        semaphore.release();
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException("Batch fetch benchmark interrupted", e);
        }
        
        metrics.stop();
        return metrics.getResults();
    }

    private void runScenario3(String scale, int concurrency) {
        Map<String, Object> scaleConfig = config.getScaleConfig(scale);
        int orderCount = (Integer) scaleConfig.get("order_count");
        int itemsMin = (Integer) scaleConfig.get("items_per_order_min");
        int itemsMax = (Integer) scaleConfig.get("items_per_order_max");
        double priceMin = ((Number) scaleConfig.get("unit_price_min")).doubleValue();
        double priceMax = ((Number) scaleConfig.get("unit_price_max")).doubleValue();
        int qtyMin = (Integer) scaleConfig.get("quantity_min");
        int qtyMax = (Integer) scaleConfig.get("quantity_max");

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String outputDir = String.format("results/scenario3-%s-c%d-%s", scale, concurrency, timestamp);
        
        try (MongoDBConnection mongoConn = new MongoDBConnection(config);
             PostgreSQLConnection pgConn = new PostgreSQLConnection(config)) {
            
            MongoDBMultiCollectionScenario mongoScenario = new MongoDBMultiCollectionScenario(mongoConn);
            com.benchmark.scenarios.scenario3.PostgreSQLJsonbScenarioS3 pgScenario = new com.benchmark.scenarios.scenario3.PostgreSQLJsonbScenarioS3(pgConn);
            
            mongoScenario.setup();
            pgScenario.setup();
            
            BenchmarkResults results = new BenchmarkResults("scenario3", scale, concurrency);
            
            logger.info("PostgreSQL: INSERT benchmark");
            List<UUID> pgOrderIds = new ArrayList<>();
            results.addResult("insert", "postgresql", runInsertBenchmark(pgScenario, orderCount, itemsMin, itemsMax, priceMin, priceMax, qtyMin, qtyMax, concurrency, pgOrderIds));
            
            logger.info("MongoDB: INSERT benchmark");
            List<UUID> mongoOrderIds = new ArrayList<>();
            results.addResult("insert", "mongodb", runInsertBenchmark(mongoScenario, orderCount, itemsMin, itemsMax, priceMin, priceMax, qtyMin, qtyMax, concurrency, mongoOrderIds));
            
            logger.info("PostgreSQL: APPEND benchmark");
            results.addResult("append", "postgresql", runAppendBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("MongoDB: APPEND benchmark");
            results.addResult("append", "mongodb", runAppendBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("PostgreSQL: UPDATE benchmark");
            results.addResult("update", "postgresql", runUpdateBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("MongoDB: UPDATE benchmark");
            results.addResult("update", "mongodb", runUpdateBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("PostgreSQL: DELETE benchmark");
            results.addResult("delete", "postgresql", runDeleteBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("MongoDB: DELETE benchmark");
            results.addResult("delete", "mongodb", runDeleteBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("PostgreSQL: BATCH_INSERT benchmark");
            results.addResult("batch_insert", "postgresql", runBatchInsertBenchmark(pgScenario, orderCount, itemsMin, itemsMax, priceMin, priceMax, qtyMin, qtyMax, concurrency));
            
            logger.info("MongoDB: BATCH_INSERT benchmark");
            results.addResult("batch_insert", "mongodb", runBatchInsertBenchmark(mongoScenario, orderCount, itemsMin, itemsMax, priceMin, priceMax, qtyMin, qtyMax, concurrency));
            
            logger.info("PostgreSQL: FETCH_ORDER benchmark");
            results.addResult("fetch_order", "postgresql", runFetchBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("MongoDB: FETCH_ORDER benchmark");
            results.addResult("fetch_order", "mongodb", runFetchBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("PostgreSQL: FETCH_FILTERED benchmark");
            results.addResult("fetch_filtered", "postgresql", runFetchFilteredBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("MongoDB: FETCH_FILTERED benchmark");
            results.addResult("fetch_filtered", "mongodb", runFetchFilteredBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("PostgreSQL: COUNT benchmark");
            results.addResult("count", "postgresql", runCountBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("MongoDB: COUNT benchmark");
            results.addResult("count", "mongodb", runCountBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("PostgreSQL: AGGREGATE benchmark");
            results.addResult("aggregate", "postgresql", runAggregateBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("MongoDB: AGGREGATE benchmark");
            results.addResult("aggregate", "mongodb", runAggregateBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("PostgreSQL: BATCH_FETCH benchmark");
            results.addResult("batch_fetch", "postgresql", runBatchFetchBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("MongoDB: BATCH_FETCH benchmark");
            results.addResult("batch_fetch", "mongodb", runBatchFetchBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("Writing results");
            ResultsWriter writer = new ResultsWriter(outputDir);
            writer.writeResults(results);
            
            mongoScenario.cleanup();
            pgScenario.cleanup();
            
        } catch (Exception e) {
            logger.error("Benchmark failed", e);
        }
    }

    private void runScenario4(String scale, int concurrency) {
        Map<String, Object> scaleConfig = config.getScaleConfig(scale);
        int orderCount = (Integer) scaleConfig.get("order_count");
        int itemsMin = (Integer) scaleConfig.get("items_per_order_min");
        int itemsMax = (Integer) scaleConfig.get("items_per_order_max");
        double priceMin = ((Number) scaleConfig.get("unit_price_min")).doubleValue();
        double priceMax = ((Number) scaleConfig.get("unit_price_max")).doubleValue();
        int qtyMin = (Integer) scaleConfig.get("quantity_min");
        int qtyMax = (Integer) scaleConfig.get("quantity_max");

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String outputDir = String.format("results/scenario4-%s-c%d-%s", scale, concurrency, timestamp);
        
        try (MongoDBConnection mongoConn = new MongoDBConnection(config);
             PostgreSQLConnection pgConn = new PostgreSQLConnection(config)) {
            
            com.benchmark.scenarios.scenario4.MongoDBMultiCollectionScenario mongoScenario = 
                new com.benchmark.scenarios.scenario4.MongoDBMultiCollectionScenario(mongoConn);
            com.benchmark.scenarios.scenario4.PostgreSQLNormalizedScenario pgScenario = 
                new com.benchmark.scenarios.scenario4.PostgreSQLNormalizedScenario(pgConn);
            
            mongoScenario.setup();
            pgScenario.setup();
            
            BenchmarkResults results = new BenchmarkResults("scenario4", scale, concurrency);
            
            logger.info("Running PostgreSQL (Normalized) benchmarks...");
            logger.info("PostgreSQL: INSERT benchmark (seeding data)");
            List<UUID> pgOrderIds = new ArrayList<>();
            results.addResult("insert", "postgresql", runInsertBenchmark(pgScenario, orderCount, itemsMin, itemsMax, priceMin, priceMax, qtyMin, qtyMax, concurrency, pgOrderIds));
            
            logger.info("PostgreSQL: APPEND benchmark");
            results.addResult("append", "postgresql", runAppendBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: UPDATE benchmark");
            results.addResult("update", "postgresql", runUpdateBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: DELETE benchmark");
            results.addResult("delete", "postgresql", runDeleteBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: BATCH_INSERT benchmark");
            results.addResult("batch_insert", "postgresql", runBatchInsertBenchmark(pgScenario, orderCount, itemsMin, itemsMax, priceMin, priceMax, qtyMin, qtyMax, concurrency));
            
            logger.info("PostgreSQL: FETCH_ORDER benchmark");
            results.addResult("fetch_order", "postgresql", runFetchBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: FETCH_FILTERED benchmark");
            results.addResult("fetch_filtered", "postgresql", runFetchFilteredBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: COUNT benchmark");
            results.addResult("count", "postgresql", runCountBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: AGGREGATE benchmark");
            results.addResult("aggregate", "postgresql", runAggregateBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("PostgreSQL: BATCH_FETCH benchmark");
            results.addResult("batch_fetch", "postgresql", runBatchFetchBenchmark(pgScenario, pgOrderIds, concurrency));
            
            logger.info("Running MongoDB (Multi-Collection) benchmarks...");
            logger.info("MongoDB: INSERT benchmark (seeding data)");
            List<UUID> mongoOrderIds = new ArrayList<>();
            results.addResult("insert", "mongodb", runInsertBenchmark(mongoScenario, orderCount, itemsMin, itemsMax, priceMin, priceMax, qtyMin, qtyMax, concurrency, mongoOrderIds));
            
            logger.info("MongoDB: APPEND benchmark");
            results.addResult("append", "mongodb", runAppendBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: UPDATE benchmark");
            results.addResult("update", "mongodb", runUpdateBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: DELETE benchmark");
            results.addResult("delete", "mongodb", runDeleteBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: BATCH_INSERT benchmark");
            results.addResult("batch_insert", "mongodb", runBatchInsertBenchmark(mongoScenario, orderCount, itemsMin, itemsMax, priceMin, priceMax, qtyMin, qtyMax, concurrency));
            
            logger.info("MongoDB: FETCH_ORDER benchmark");
            results.addResult("fetch_order", "mongodb", runFetchBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: FETCH_FILTERED benchmark");
            results.addResult("fetch_filtered", "mongodb", runFetchFilteredBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: COUNT benchmark");
            results.addResult("count", "mongodb", runCountBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: AGGREGATE benchmark");
            results.addResult("aggregate", "mongodb", runAggregateBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("MongoDB: BATCH_FETCH benchmark");
            results.addResult("batch_fetch", "mongodb", runBatchFetchBenchmark(mongoScenario, mongoOrderIds, concurrency));
            
            logger.info("Writing results");
            ResultsWriter writer = new ResultsWriter(outputDir);
            writer.writeResults(results);
            
            mongoScenario.cleanup();
            pgScenario.cleanup();
            
        } catch (Exception e) {
            logger.error("Scenario 4 benchmark failed", e);
        }
    }
}

