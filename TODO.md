# MongoDB vs PostgreSQL Benchmark - TODO List

## Phase 1: Environment Setup

### Docker Infrastructure
- [ ] Create `docker` directory structure
- [ ] Create `docker-compose.yml` for all scenarios
- [ ] Create `docker/init-postgres.sql` script with:
  - [ ] `orders_jsonb` table (Scenario 1 & 3)
  - [ ] `orders_normalized` and `items_normalized` tables (Scenario 2 & 4)
  - [ ] GIN indexes for JSONB columns
  - [ ] B-tree indexes for normalized tables
  - [ ] Trigger function `update_order_amount()` for auto-calculation
- [ ] Create `docker/init-mongo.sh` script with:
  - [ ] Replica set initialization
  - [ ] `orders_embedded` collection indexes (Scenario 1 & 2)
  - [ ] `orders_normalized` and `items_normalized` collection indexes (Scenario 3 & 4)
- [ ] Test Docker Compose setup with resource limits (2 CPUs, 3GB RAM each)
- [ ] Verify MongoDB replica set initialization
- [ ] Verify PostgreSQL initialization and triggers

### Configuration
- [ ] Create `config.yaml` with all configuration parameters
- [ ] Define scale presets: micro, small, medium, large
- [ ] Configure concurrency levels: 1, 10, 50, 100
- [ ] Configure connection pool settings
- [ ] Configure operation mix percentages
- [ ] Configure metrics collection settings

## Phase 2: Java Implementation (Primary)

### Project Setup
- [ ] Create Java project structure in `java/` directory
- [ ] Create `build.gradle` with all dependencies
- [ ] Create `settings.gradle`
- [ ] Configure Java 25 toolchain
- [ ] Setup logging with `logback.xml`

### Core Models
- [ ] Create `Order.java` model class
- [ ] Create `Item.java` model class
- [ ] Create enums for status fields (OrderStatus, ItemStatus)

### Database Clients
- [ ] Implement MongoDB connection manager with replica set support
- [ ] Implement PostgreSQL connection pool with HikariCP
- [ ] Create connection configuration from YAML
- [ ] Test database connections

### Test Data Generation
- [ ] Create `DataGenerator.java` for generating fake data
- [ ] Implement order generation with JavaFaker
- [ ] Implement item generation with configurable quantities
- [ ] Support different distributions: uniform, normal, zipfian
- [ ] Implement hot-record selection for zipfian distribution

### Metrics Collection
- [ ] Create `MetricsCollector.java` interface
- [ ] Implement latency measurement (p50, p95, p99, avg, min, max)
- [ ] Implement throughput calculation (ops/sec)
- [ ] Implement CPU monitoring
- [ ] Implement memory monitoring
- [ ] Implement disk I/O monitoring
- [ ] Implement network I/O monitoring
- [ ] Implement database storage size measurement (MB)
- [ ] Implement index size measurement (MB)
- [ ] Implement query execution time tracking
- [ ] Implement connection pool utilization monitoring
- [ ] Create `MetricsResult.java` for storing results

### Scenario 1: Embedded vs JSONB
- [ ] Implement MongoDB embedded document operations:
  - [ ] INSERT: Create order with embedded items
  - [ ] APPEND: Add items to embedded array using `$push`
  - [ ] UPDATE: Update item in embedded array using positional operator
  - [ ] DELETE: Remove item from embedded array using `$pull`
  - [ ] BATCH_INSERT: Bulk insert orders with items
  - [ ] FETCH_ORDER: Retrieve order with all items
  - [ ] FETCH_FILTERED: Query with array filters
  - [ ] COUNT: Count items in array using `$size`
  - [ ] AGGREGATE: Sum amounts using aggregation pipeline
  - [ ] BATCH_FETCH: Retrieve multiple orders
- [ ] Implement PostgreSQL JSONB operations:
  - [ ] INSERT: Create order with JSONB items
  - [ ] APPEND: Append to JSONB array using `||`
  - [ ] UPDATE: Update JSONB element using `jsonb_set`
  - [ ] DELETE: Remove from JSONB array
  - [ ] BATCH_INSERT: Batch insert with JSONB
  - [ ] FETCH_ORDER: Retrieve order with JSONB items
  - [ ] FETCH_FILTERED: Query JSONB with `@>` operator
  - [ ] COUNT: Count JSONB array elements
  - [ ] AGGREGATE: Aggregate JSONB data
  - [ ] BATCH_FETCH: Batch retrieve orders
- [ ] Create benchmark runner for Scenario 1
- [ ] Add metrics collection for all operations

### Scenario 2: Embedded vs Normalized
- [ ] Implement MongoDB operations (same as Scenario 1)
- [ ] Implement PostgreSQL normalized operations:
  - [ ] INSERT: Transaction with order + items inserts
  - [ ] APPEND: Insert new items with transaction
  - [ ] UPDATE: Update item in items table
  - [ ] DELETE: Delete item (trigger updates order amount)
  - [ ] BATCH_INSERT: Batch insert orders and items
  - [ ] FETCH_ORDER: JOIN orders and items
  - [ ] FETCH_FILTERED: JOIN with WHERE clause
  - [ ] COUNT: COUNT items by order_id
  - [ ] AGGREGATE: SUM amounts by order_id
  - [ ] BATCH_FETCH: Batch JOIN operations
- [ ] Create benchmark runner for Scenario 2
- [ ] Add metrics collection for all operations

### Scenario 3: Multi-Collection vs JSONB
- [ ] Implement MongoDB multi-collection operations:
  - [ ] INSERT: Transaction with order + items documents
  - [ ] APPEND: Insert items with transaction
  - [ ] UPDATE: Update item document with transaction
  - [ ] DELETE: Delete item document with transaction
  - [ ] BATCH_INSERT: Batch insert with transactions
  - [ ] FETCH_ORDER: `$lookup` aggregation to join
  - [ ] FETCH_FILTERED: `$lookup` with `$match`
  - [ ] COUNT: Count items by order_id
  - [ ] AGGREGATE: Aggregate with `$lookup`
  - [ ] BATCH_FETCH: Batch `$lookup` operations
- [ ] Implement PostgreSQL JSONB operations (same as Scenario 1)
- [ ] Create benchmark runner for Scenario 3
- [ ] Add metrics collection for all operations

### Scenario 4: Multi-Collection vs Normalized
- [ ] Implement MongoDB operations (same as Scenario 3)
- [ ] Implement PostgreSQL operations (same as Scenario 2)
- [ ] Create benchmark runner for Scenario 4
- [ ] Add metrics collection for all operations

### Benchmark Runner
- [ ] Create `BenchmarkRunner.java` main class
- [ ] Implement scenario selection
- [ ] Implement scale selection
- [ ] Implement concurrency management using Virtual Threads
- [ ] Implement warmup phase
- [ ] Implement test execution with exact operation count
- [ ] Implement test repetition and averaging
- [ ] Implement random test ordering
- [ ] Implement cache clearing between tests
- [ ] Add progress reporting

### Results Output
- [ ] Implement CSV output with side-by-side comparison format
- [ ] Implement JSON output with nested structure
- [ ] Implement Markdown table output
- [ ] Create results directory structure
- [ ] Add timestamp to result files
- [ ] Combine PostgreSQL and MongoDB results in single file

## Phase 3: Testing & Validation

### Unit Testing
- [ ] Test data generation functions
- [ ] Test metrics calculation
- [ ] Test configuration loading
- [ ] Test database connections

### Integration Testing
- [ ] Test each operation in each scenario
- [ ] Verify transaction behavior
- [ ] Verify index usage
- [ ] Test with different data scales

### Performance Validation
- [ ] Run micro scale tests for quick validation
- [ ] Verify results are reproducible (variance < 10%)
- [ ] Verify resource limits are respected
- [ ] Verify operation counts are exact

## Phase 4: Execution & Analysis

### Benchmark Execution
- [ ] Run Scenario 1 benchmarks (all scales, all concurrency levels)
- [ ] Run Scenario 2 benchmarks (all scales, all concurrency levels)
- [ ] Run Scenario 3 benchmarks (all scales, all concurrency levels)
- [ ] Run Scenario 4 benchmarks (all scales, all concurrency levels)

### Results Analysis
- [ ] Analyze latency differences
- [ ] Analyze throughput differences
- [ ] Analyze resource utilization
- [ ] Identify performance patterns
- [ ] Create comparison charts (optional)

### Documentation
- [ ] Document benchmark findings in README
- [ ] Create performance comparison tables
- [ ] Add architectural recommendations
- [ ] Document setup and execution instructions
- [ ] Add troubleshooting guide

## Phase 6: Scripts & Automation

### Setup Scripts
- [ ] Create `scripts/setup-docker.sh` - Initialize Docker containers
- [ ] Create `scripts/cleanup-docker.sh` - Remove containers and volumes
- [ ] Create `scripts/run-benchmark.sh` - Run complete benchmark suite
- [ ] Create `scripts/verify-setup.sh` - Verify environment is ready

### Utility Scripts
- [ ] Create script to generate sample data
- [ ] Create script to clear databases
- [ ] Create script to check database sizes
- [ ] Create script to export results to different formats

## Phase 5: Final Deliverables

- [ ] Complete README with setup instructions
- [ ] Complete benchmark results documentation
- [ ] Add performance comparison visualizations
- [ ] Add architectural decision recommendations
- [ ] Code cleanup and documentation
- [ ] Final testing of all scenarios
- [ ] Repository organization and cleanup
