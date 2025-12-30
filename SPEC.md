# Benchmark Specification: MongoDB 8 vs PostgreSQL 18

## Overview
This specification outlines the functional requirements for benchmarking MongoDB 8 and PostgreSQL 18 in a scenario involving orders and items. The benchmark will compare performance, consistency, and transactional behavior across two architectural approaches:

1. **Embedded vs. JSON/BSON Storage**: Single document/row with embedded data
2. **Multi-Document/Table with Transactions**: Separate collections/tables with transactional integrity

## Entities and Relationships

### Order
- **Fields**:
  - `order_id`: Unique identifier (string or UUID)
  - `customer_id`: Identifier for the customer (string)
  - `order_date`: Timestamp of order creation
  - `total_amount`: Calculated sum of (price × quantity) for all items in the order
  - `status`: Order status (e.g., "pending", "completed", "cancelled")

### Item
- **Fields**:
  - `item_id`: Unique identifier (string or UUID)
  - `order_id`: Reference to the parent order
  - `product_id`: Identifier for the product (string)
  - `name`: Product name (string)
  - `price`: Unit price (decimal/float)
  - `quantity`: Number of units (integer)

### Relationship
- An Order contains exactly 10 Items (fixed for benchmark consistency)
- `total_amount` in Order must equal the sum of (`price` × `quantity`) for all associated Items
- Relationship is one-to-many: Order → Items

## Code Structure

### Package Structure
```
src/main/java/com/mrscrape/benchmark/
├── BenchmarkApp.java              # Main CLI application class
├── model/
│   ├── Order.java                 # Order data model class
│   └── Item.java                  # Item data model class
├── db/
│   ├── DatabaseOperations.java    # Interface for database operations
│   ├── MongoConnection.java       # MongoDB connection utilities
│   ├── PostgresConnection.java    # PostgreSQL connection utilities
│   ├── scenario1/
│   │   ├── MongoEmbeddedOps.java  # Scenario 1 MongoDB operations
│   │   └── PostgresJsonbOps.java  # Scenario 1 PostgreSQL operations
│   └── scenario2/
│       ├── MongoMultiDocOps.java # Scenario 2 MongoDB operations
│       └── PostgresMultiTableOps.java # Scenario 2 PostgreSQL operations
├── concurrency/
│   └── VirtualThreadExecutor.java # Virtual thread concurrency framework
├── metrics/
│   ├── MetricsCollector.java      # Metrics collection and timing
│   └── CsvOutput.java            # CSV output for measurement and aggregation
└── config/
    └── BenchmarkConfig.java       # Configuration and command-line parsing
```

### Key Classes Overview

#### BenchmarkApp.java
- Main entry point with PicoCLI annotations for command-line parsing
- Orchestrates the benchmark execution flow
- Handles mode selection (measurement vs aggregation)

#### Data Models (model/)
- **Order.java**: Represents order entity with Jackson annotations for JSON/BSON serialization
- **Item.java**: Represents item entity with Jackson annotations for JSON/BSON serialization

#### Database Operations (db/)
- **DatabaseOperations.java**: Interface defining contract for all database operations (insert, query, update-modify, update-add, delete)
- Connection classes handle database-specific connection management
- Scenario-specific operation classes implement the DatabaseOperations interface

#### Concurrency (concurrency/)
- **VirtualThreadExecutor.java**: Manages Java 25 virtual threads with bounded concurrency limits

#### Metrics and Output (metrics/)
- **MetricsCollector.java**: Collects timing and throughput metrics for operations
- **CsvOutput.java**: Handles CSV writing for measurement mode and aggregation mode

#### Configuration (config/)
- **BenchmarkConfig.java**: Command-line argument parsing and validation

## Benchmarking Scenarios

### Scenario 1: Embedded Storage
- **MongoDB**: Single document per order with items embedded as an array
- **PostgreSQL**: Single row per order with items stored as BSON/JSON in a column
- **Operations**:
  - Insert: Create order with embedded items, calculate and store total_amount
  - Update: Modify item quantities/prices, recalculate total_amount atomically
  - Update: Add 5 items to an order, recalculate total_amount atomically
  - Query: Retrieve order with all items, validate total_amount consistency
  - Delete: Remove order and all associated items

#### Schema
- **MongoDB**:
  ```json
  {
    "_id": "order_id",
    "customer_id": "string",
    "order_date": "timestamp",
    "total_amount": "big integer",
    "status": "string",
    "items": [
      {
        "item_id": "string",
        "product_id": "string",
        "name": "string",
        "price": "big integer",
        "quantity": "big integer"
      }
    ]
  }
  ```
- **PostgreSQL**:
  ```sql
  CREATE TABLE orders (
    order_id VARCHAR PRIMARY KEY,
    customer_id VARCHAR,
    order_date TIMESTAMP,
    total_amount BIGINT,
    status VARCHAR,
    items JSONB
  );
  ```
  *Note: No triggers used; total_amount calculation handled atomically in application code.*

#### Indexes
- **MongoDB**: Default index on `_id` (order_id) for retrieving orders by ID.
- **PostgreSQL**: Primary key index on `order_id` for retrieving orders by ID.

### Scenario 2: Multi-Document/Table with Transactions
- **MongoDB**: Separate collections for orders and items, use multi-document transactions
- **PostgreSQL**: Separate tables for orders and items, use ACID transactions
- **Operations**:
  - Insert: Create order, then create 10 items, calculate and store total_amount (transactional)
  - Update: Modify items, recalculate total_amount (transactional)
  - Update: Add 5 items to an order, recalculate total_amount (transactional)
  - Query: **JOIN/AGGREGATE** order with items using aggregation pipeline ($lookup) or SQL JOIN, validate total_amount consistency
  - Delete: Remove order and items (transactional)

#### Schema
- **MongoDB**:
  - Orders collection:
    ```json
    {
      "_id": "order_id",
      "customer_id": "string",
      "order_date": "timestamp",
      "total_amount": "big integer",
      "status": "string"
    }
    ```
  - Items collection:
    ```json
    {
      "_id": "item_id",
      "order_id": "string",
      "product_id": "string",
      "name": "string",
      "price": "big integer",
      "quantity": "big integer"
    }
    ```
- **PostgreSQL**:
  ```sql
  CREATE TABLE orders (
    order_id VARCHAR PRIMARY KEY,
    customer_id VARCHAR,
    order_date TIMESTAMP,
    total_amount BIGINT,
    status VARCHAR
  );

  CREATE TABLE items (
    item_id VARCHAR PRIMARY KEY,
    order_id VARCHAR REFERENCES orders(order_id),
    product_id VARCHAR,
    name VARCHAR,
    price BIGINT,
    quantity BIGINT
  );
  ```
  *Note: No triggers used; total_amount calculation and referential integrity handled atomically in application code.*

#### Indexes
- **MongoDB**: 
  - Orders collection: Default index on `_id` (order_id) for retrieving orders by ID.
  - Items collection: Index on `order_id` for efficient joins and querying items by order.
- **PostgreSQL**: 
  - Orders table: Primary key index on `order_id` for retrieving orders by ID.
  - Items table: Index on `order_id` (foreign key) for joins and querying items by order.

## Functional Requirements

### Data Integrity
- `total_amount` must always reflect the accurate sum of item calculations
- No orphaned items (items without orders) allowed
- Referential integrity maintained across operations

### Consistency
- Atomic operations: All changes to order and items succeed or fail together
- Read consistency: Queries return consistent state of order and items

### Performance Metrics (To Be Measured)
- Throughput: Operations per second for each operation type
- Latency: Response time percentiles (p50, p75, p99) for each operation type

### Transaction Requirements
- For Scenario 2: Full transactional support ensuring ACID properties
- Rollback on failure: Incomplete operations must not leave partial state
- Isolation: Concurrent operations do not interfere with each other

### Test Data Generation
- Generate synthetic orders with exactly 10 items each
- Vary data sizes: Small (few KB), medium (hundreds KB), large (MB+)
- Use big integers for prices and quantities; assume positive values and non-zero quantities

### Validation
- Post-operation checks: Verify total_amount calculations
- Consistency audits: Ensure no data corruption after operations
- Error handling: All operations should implement retry logic with exponential backoff and jitter on exceptions; if all retries fail, the program should exit with an error
- Graceful failure and recovery mechanisms

## Implementation Notes

### Core Technologies
- **Language & Runtime**: Java 25 with virtual threads for lightweight concurrency
- **Drivers**: MongoDB Java Driver (official), PostgreSQL JDBC Driver (official)
- **Build System**: Gradle with dependency management
- **Logging**: SLF4J for structured operation tracking

### Connection Pooling Configuration
Both databases are configured with equivalent, scaled connection pools to ensure fair comparison under high concurrency:

**PostgreSQL (HikariCP)**:
- `maximumPoolSize`: 300 connections (scaled for CONCURRENCY=300)
- `minimumIdle`: 20 idle connections (maintains pool warmth)
- `connectionTimeout`: 30 seconds (fail fast on pool exhaustion)
- `idleTimeout`: 60 seconds (connection eviction threshold)
- `maxLifetime`: 5 minutes (connection lifetime limit)
- `leakDetectionThreshold`: 60 seconds (detect and log leaked connections)
- `connectionTestQuery`: "SELECT 1" (validate connection health)
- **Database Configuration**: PostgreSQL container launched with `max_connections=300` parameter

**MongoDB (Native Connection Pool)**:
- `maxSize`: 300 connections (matches PostgreSQL pool size)
- `minSize`: 20 idle connections (maintains pool warmth)
- `maxWaitTime`: 30 seconds (timeout waiting for available connection)
- `maxConnectionIdleTime`: 60 seconds (idle connection eviction)
- `maxConnectionLifeTime`: 5 minutes (connection lifetime limit)

### Retry Mechanism with Exponential Backoff
All database operations implement retry logic via `RetryUtil` class:
- **Strategy**: Exponential backoff with jitter for transient failure resilience
- **Default Configuration**:
  - `maxRetries`: 3 attempts
  - `initialBackoffMs`: 100 milliseconds
  - `backoffMultiplier`: 2x exponential (100ms → 200ms → 400ms)
  - `jitterMs`: Random value up to backoffMs (reduces thundering herd)
- **Implementation**: Both `executeWithRetry()` (returning results) and `executeVoidWithRetry()` (void operations)
- **Logging**: Warning logs for retries, error logs for final failures with stack traces

### Concurrency Framework
Virtual threads are managed via `VirtualThreadExecutor` class:
- **Executor**: `Executors.newVirtualThreadPerTaskExecutor()` for lightweight virtual thread creation
- **Bounded Concurrency**: `Semaphore(maxConcurrency)` limits active concurrent operations
- **Task Tracking**: `AtomicInteger pendingTasks` tracks submitted vs. completed tasks
- **Exception Handling**: Thread-safe exception collection via `CopyOnWriteArrayList`
- **Graceful Shutdown**: `awaitCompletion()` waits for all tasks to complete or timeout

### Connection Lifecycle Management
All database operations follow strict connection lifecycle patterns:
- **Try-With-Resources Pattern**: All `Connection` objects wrapped in try-with-resources for automatic closure
- **No Nested Connections**: Query operations do NOT call validation methods that require additional connections (prevents deadlock)
- **Per-Operation Connection**: Each operation acquires a fresh connection from the pool, executes atomically, then releases
- **Error Propagation**: Exceptions properly propagate while ensuring connection closure

### Database Schema Design

#### Scenario 1: Embedded/JSONB Storage
- **MongoDB**: Orders stored as single documents with `items` array embedded in order document
- **PostgreSQL**: Orders stored as single rows with `items` array in JSONB column
- **Advantage**: Single-document/row atomicity, simplest transactional model
- **Limitation**: Limited query flexibility on nested array elements

#### Scenario 2: Multi-Document/Multi-Table Storage
- **MongoDB**: Separate `orders` and `items` collections with referential links
- **PostgreSQL**: Separate `orders` and `items` tables with foreign key relationships
- **Advantage**: Normalized schema, flexible querying, relational integrity
- **Complexity**: Requires explicit transaction handling, multi-step operations

### Key Implementation Details

**Insert Operation**:
- Generates sequential order IDs (0 to insert_count-1)
- Creates 10 items per order with calculated total_amount
- Scenario 1: Single document/row insert (atomic at DB level)
- Scenario 2: Order insert + 10 item inserts within transaction (atomic via transaction)

**Update Operations**:
- **Update-Modify**: Modifies quantities/prices of existing items, recalculates total_amount
- **Update-Add**: Adds 5 new items to existing order, recalculates total_amount
- All updates verify data consistency before commit

**Query Operation**:
- Retrieves full order with all items
- Scenario 1: Single document/row fetch
- Scenario 2: Order + all associated items (join on Scenario 2)
- Validates retrieved total_amount matches calculation (post-query validation, not during)

**Delete Operation**:
- Removes order and all associated items
- Scenario 1: Single document/row deletion
- Scenario 2: Order deletion (cascades to items via foreign key)

### Important Design Decisions
- **No Intra-Operation Validation**: Query operations do NOT call `validateTotalAmount()` internally to prevent connection pool deadlock under high concurrency
- **Post-Operation Validation**: Consistency checks performed after operations complete, using separate connection
- **Atomic Operations**: Both scenarios ensure order + items changes are atomic (single doc/row in Scenario 1, transaction in Scenario 2)
- **Connection Pool Matching**: Both databases configured with identical pool sizes and timeouts for fair comparison
- Environment: Docker containers for MongoDB 8 and PostgreSQL 18 on identical hardware (2-core CPU, 2 GB RAM, SSD storage)
- Operation Execution Flow:
  - Inserts: Generate sequential order IDs from 0 to (insert_count-1), perform insertions asynchronously using virtual threads bounded by concurrency level
  - Update-Modify: Execute update-modify-count operations asynchronously with virtual threads bounded by concurrency level, randomly selecting order IDs from 0 to (insert_count-1)
  - Update-Add: Execute update-add-count operations asynchronously with virtual threads bounded by concurrency level, randomly selecting order IDs from 0 to (insert_count-1)
  - Queries: Execute query-count operations asynchronously with virtual threads bounded by concurrency level, randomly selecting order IDs from 0 to (insert_count-1)
  - Deletes: Execute delete-count operations sequentially with order IDs from 0 to (insert_count-1), asynchronously using virtual threads bounded by concurrency level
- Reporting: 
  - Measurement mode: CSV output with ";" delimiter, format: operation_name_metric_name_measure_unit|value
  - Aggregation mode: CSV output with ";" delimiter, header: operation_name_metric_name_measure_unit|filename1|filename2|..., rows: operation_name_metric_name_measure_unit|value1|value2|...

### Shell Script for Automated Benchmarking
The following steps must be executed in sequence:

1. **Cleanup**: Ensure PostgreSQL and MongoDB Docker containers and volumes are removed
2. **PostgreSQL Provisioning**: Start PostgreSQL Docker container
3. **PostgreSQL Benchmark**: Run benchmark app in measurement mode with PostgreSQL, generate output file
4. **PostgreSQL Cleanup**: Stop and remove PostgreSQL Docker container
5. **MongoDB Provisioning**: Start MongoDB Docker container
6. **MongoDB Benchmark**: Run benchmark app in measurement mode with MongoDB using same inputs, generate output file
7. **MongoDB Cleanup**: Stop and remove MongoDB Docker container
8. **Aggregation**: Run benchmark app in aggregation mode with both output files to generate final aggregated results

### Command-Line Parameters
- `--mode`: Mode of operation - "measurement" (run benchmark and generate metrics) or "aggregation" (combine results from multiple measurement files)
- `--scenario`: Choice between Scenario 1 (Embedded) or Scenario 2 (Multi-Document/Table) [required for measurement mode]
- `--database`: MongoDB or PostgreSQL [required for measurement mode]
- `--concurrency`: Number of concurrent virtual threads [required for measurement mode]
- **Operation Counts** [required for measurement mode]:
  - `--insert-count`: Number of insert operations (create new orders with 10 items)
  - `--update-modify-count`: Number of update operations that modify existing item quantities/prices
  - `--update-add-count`: Number of update operations that add 5 items to existing orders
  - `--query-count`: Number of query operations (retrieve orders with all items)
  - `--delete-count`: Number of delete operations (remove orders and all associated items)
- `--connection-string`: Database connection details (host, port, credentials) [required for measurement mode]
- `--output-file`: Path for results/metrics output [required for both modes]
- `--input-files`: Comma-separated list of measurement CSV files to aggregate [required for aggregation mode]