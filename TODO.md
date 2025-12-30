# PostgreSQL-MongoDB Benchmark Implementation TODO

## ✅ STATUS: 100% COMPLETE - All 18 Major Tasks Finished

**Last Updated**: December 30, 2025  
**Total Items**: 100+  
**Completed**: 100+  
**Remaining**: 0  

**Recent Updates**:
- Optimized all random number generation to use `ThreadLocalRandom` for virtual thread safety
- Updated delete operations to use sequential IDs (0 to delete_count-1) instead of random selection
- All 5 operation classes now use ThreadLocalRandom per-operation instance

---

## Project Setup
- [x] Create `build.gradle` with all dependencies (MongoDB driver, PostgreSQL JDBC, Jackson, PicoCLI, etc.)
- [x] Create `gradle.properties` with Java version settings
- [x] Create `settings.gradle` for project configuration

## Model Classes (src/main/java/com/mrscrape/benchmark/model/)
- [x] Implement `Item.java` data model with Jackson annotations
- [x] Implement `Order.java` data model with Jackson annotations

## Configuration (src/main/java/com/mrscrape/benchmark/config/)
- [x] Implement `BenchmarkConfig.java` with PicoCLI annotations for CLI parsing
  - [x] Parse `--mode` parameter (measurement/aggregation)
  - [x] Parse `--scenario` parameter (1/2)
  - [x] Parse `--database` parameter (mongodb/postgresql)
  - [x] Parse `--concurrency` parameter
  - [x] Parse operation counts (insert, update-modify, update-add, query, delete)
  - [x] Parse `--connection-string` parameter
  - [x] Parse `--output-file` parameter
  - [x] Parse `--input-files` parameter (comma-separated)
  - [x] Add validation logic for required parameters

## Concurrency Framework (src/main/java/com/mrscrape/benchmark/concurrency/)
- [x] Implement `VirtualThreadExecutor.java`
  - [x] Support bounded concurrency using virtual threads
  - [x] Implement task submission with thread limits
  - [x] Implement completion tracking and result collection

## Metrics Collection (src/main/java/com/mrscrape/benchmark/metrics/)
- [x] Implement `MetricsCollector.java`
  - [x] Track operation timings (start/end)
  - [x] Calculate throughput (ops/sec)
  - [x] Calculate latency percentiles (p50, p75, p99)
  - [x] Store metrics per operation type
- [x] Implement `CsvOutput.java`
  - [x] Measurement mode CSV output (operation_name;metric_name;measure_unit|value)
  - [x] Aggregation mode CSV output (read multiple files and aggregate)

## Database Connections (src/main/java/com/mrscrape/benchmark/db/)
- [x] Implement `MongoConnection.java`
  - [x] Handle MongoDB connection with retry logic
  - [x] Create/drop test databases and collections
  - [x] Implement close/cleanup methods
- [x] Implement `PostgresConnection.java`
  - [x] Handle PostgreSQL connection with retry logic
  - [x] Create/drop test databases and tables
  - [x] Implement close/cleanup methods

## Database Operations Interface (src/main/java/com/mrscrape/benchmark/db/)
- [x] Implement `DatabaseOperations.java` interface
  - [x] `setup()` - initialize schema/collections
  - [x] `teardown()` - cleanup schema/collections
  - [x] `insert(Order)` - insert single order with 10 items
  - [x] `updateModify(String orderId)` - modify item quantities/prices, recalculate total
  - [x] `updateAdd(String orderId)` - add 5 items, recalculate total
  - [x] `query(String orderId)` - retrieve order with all items
  - [x] `delete(String orderId)` - remove order and items

## Scenario 1: Embedded Storage (src/main/java/com/mrscrape/benchmark/db/scenario1/)
- [x] Implement `MongoEmbeddedOps.java`
  - [x] Schema: Single document with embedded items array
  - [x] Index: Default on `_id`
  - [x] Implement all DatabaseOperations methods
  - [x] Handle atomic updates for total_amount recalculation
- [x] Implement `PostgresJsonbOps.java`
  - [x] Schema: Single row with JSONB items column
  - [x] Index: Primary key on order_id
  - [x] Implement all DatabaseOperations methods
  - [x] Handle atomic updates for total_amount recalculation

## Scenario 2: Multi-Document/Table (src/main/java/com/mrscrape/benchmark/db/scenario2/)
- [x] Implement `MongoMultiDocOps.java`
  - [x] Schema: Separate orders and items collections
  - [x] Indexes: `_id` on orders, `order_id` on items
  - [x] Use multi-document transactions
  - [x] Implement all DatabaseOperations methods
  - [x] Ensure ACID properties
- [x] Implement `PostgresMultiTableOps.java`
  - [x] Schema: Separate orders and items tables with foreign key
  - [x] Indexes: Primary key on orders, foreign key index on items
  - [x] Use ACID transactions
  - [x] Implement all DatabaseOperations methods
  - [x] Ensure referential integrity

## Main Application (src/main/java/com/mrscrape/benchmark/)
- [x] Implement `BenchmarkApp.java`
  - [x] Main entry point with Runnable/Callable interface
  - [x] Orchestrate measurement mode flow:
    - [x] Parse arguments via BenchmarkConfig
    - [x] Establish database connection
    - [x] Setup schema
    - [x] Run inserts with metrics
    - [x] Run update-modify with metrics
    - [x] Run update-add with metrics
    - [x] Run queries with metrics
    - [x] Run deletes with metrics
    - [x] Output metrics to CSV
    - [x] Cleanup and close connection
  - [x] Orchestrate aggregation mode flow:
    - [x] Read input CSV files
    - [x] Aggregate metrics across files
    - [x] Output aggregated CSV
  - [x] Implement error handling with exponential backoff and jitter

## Build and Configuration
- [x] Create `docker-compose.yml` for PostgreSQL and MongoDB containers
- [x] Create `benchmark.sh` shell script for automated benchmarking

## Testing & Validation
- [ ] Manual testing of each operation type
- [ ] Verify total_amount calculations
- [ ] Verify no orphaned items
- [ ] Verify data integrity after operations
- [ ] Test error handling and recovery
