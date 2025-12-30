# PostgreSQL-MongoDB Benchmark

A comprehensive benchmarking tool to compare performance and consistency between MongoDB 8 and PostgreSQL 18 with two architectural approaches: embedded storage and multi-document/table with transactions.

## Features

- **Two Scenarios**:
  - **Scenario 1: Embedded Storage** - Single document/row with embedded items
  - **Scenario 2: Multi-Document/Table** - Separate collections/tables with transactional integrity

- **Two Database Engines**:
  - MongoDB 8 with multi-document transactions
  - PostgreSQL 18 with ACID transactions

- **Virtual Thread Support**: Leverages Java 25 virtual threads for efficient concurrent operations

- **Comprehensive Metrics**:
  - Throughput (ops/sec)
  - Latency percentiles (p50, p75, p99)
  - Average duration

## Requirements

- Java 25 or higher
- Docker and Docker Compose
- Gradle 7.0 or higher

## Building

```bash
./gradlew clean build -x test
```

## Running Benchmarks

### Using the Automated Script

```bash
# Run with default parameters (Scenario 1, 100 inserts, 10 concurrency)
./benchmark.sh

# Run with custom parameters
SCENARIO=2 CONCURRENCY=20 INSERT_COUNT=500 ./benchmark.sh
```

### Running Measurements Manually

#### PostgreSQL with Scenario 1 (Embedded):

```bash
java -jar build/libs/postgresql-mongodb-benchmark-1.0.0.jar \
  --mode measurement \
  --scenario 1 \
  --database postgresql \
  --concurrency 10 \
  --insert-count 100 \
  --update-modify-count 50 \
  --update-add-count 50 \
  --query-count 100 \
  --delete-count 50 \
  --connection-string "jdbc:postgresql://localhost:5432/benchmark_db?user=benchmark&password=benchmark_password" \
  --output-file postgres_results.csv
```

#### MongoDB with Scenario 2 (Multi-Document):

```bash
java -jar build/libs/postgresql-mongodb-benchmark-1.0.0.jar \
  --mode measurement \
  --scenario 2 \
  --database mongodb \
  --concurrency 10 \
  --insert-count 100 \
  --update-modify-count 50 \
  --update-add-count 50 \
  --query-count 100 \
  --delete-count 50 \
  --connection-string "mongodb://localhost:27017/benchmark_db" \
  --output-file mongodb_results.csv
```

### Running Aggregation

```bash
java -jar build/libs/postgresql-mongodb-benchmark-1.0.0.jar \
  --mode aggregation \
  --input-files postgres_results.csv,mongodb_results.csv \
  --output-file aggregated_results.csv
```

## Command-Line Parameters

### Measurement Mode

- `--mode`: "measurement" (required)
- `--scenario`: 1 or 2 (required)
- `--database`: "mongodb" or "postgresql" (required)
- `--concurrency`: Number of concurrent virtual threads (required)
- `--insert-count`: Number of insert operations (required)
- `--update-modify-count`: Number of update-modify operations (required)
- `--update-add-count`: Number of update-add operations (required)
- `--query-count`: Number of query operations (required)
- `--delete-count`: Number of delete operations (required)
- `--connection-string`: Database connection string (required)
- `--output-file`: Output CSV file path (required)

### Aggregation Mode

- `--mode`: "aggregation" (required)
- `--input-files`: Comma-separated list of measurement CSV files (required)
- `--output-file`: Output CSV file path (required)

## Project Structure

```
src/main/java/com/mrscrape/benchmark/
├── BenchmarkApp.java                 # Main application
├── model/
│   ├── Order.java                    # Order entity
│   └── Item.java                     # Item entity
├── db/
│   ├── DatabaseOperations.java       # Interface for DB operations
│   ├── MongoConnection.java          # MongoDB connection
│   ├── PostgresConnection.java       # PostgreSQL connection
│   ├── scenario1/
│   │   ├── MongoEmbeddedOps.java    # MongoDB embedded operations
│   │   └── PostgresJsonbOps.java    # PostgreSQL JSONB operations
│   └── scenario2/
│       ├── MongoMultiDocOps.java    # MongoDB multi-doc operations
│       └── PostgresMultiTableOps.java # PostgreSQL multi-table operations
├── concurrency/
│   └── VirtualThreadExecutor.java    # Virtual thread executor
├── metrics/
│   ├── MetricsCollector.java        # Metrics collection
│   └── CsvOutput.java               # CSV output handling
└── config/
    └── BenchmarkConfig.java          # Configuration and CLI parsing
```

## Scenario Details

### Scenario 1: Embedded Storage

- **MongoDB**: Single document with embedded items array
- **PostgreSQL**: Single row with JSONB items column
- Each order contains exactly 10 items
- Operations: insert, update-modify, update-add, query, delete

### Scenario 2: Multi-Document/Table with Transactions

- **MongoDB**: Separate orders and items collections with multi-document transactions
- **PostgreSQL**: Separate orders and items tables with foreign keys and ACID transactions
- Each order contains exactly 10 items initially, can be extended with update-add
- Operations: insert, update-modify, update-add, query, delete (all transactional)

## Performance Metrics

The benchmark measures:

- **Throughput**: Operations per second
- **Latency**:
  - p50 (median)
  - p75 (75th percentile)
  - p99 (99th percentile)
- **Average Duration**: Mean operation time

## Data Validation

Each operation includes:

- Total amount verification (sum of item quantities × prices)
- Referential integrity checks
- No orphaned items
- Atomic operation guarantees (Scenario 2)

## Docker Setup

The `docker-compose.yml` provides:

- **PostgreSQL 18**: Running on port 5432
- **MongoDB 8**: Running on port 27017

Start services:
```bash
docker-compose up -d
```

Stop services:
```bash
docker-compose down
```

## Output Format

### Measurement Mode CSV

```
operation_name;metric_name;measure_unit|value
insert;throughput;ops_per_sec|1000.5
insert;avg_duration;ms|0.95
insert;p50;ms|0.85
insert;p75;ms|1.02
insert;p99;ms|2.50
```

### Aggregation Mode CSV

```
metric_name;postgres_results.csv;mongodb_results.csv
insert;throughput;ops_per_sec|1000.5|950.3
insert;avg_duration;ms|0.95|1.02
```

## Troubleshooting

### Connection Issues

- Ensure Docker containers are running: `docker-compose up -d`
- Check database connectivity: `telnet localhost 5432` or `telnet localhost 27017`
- Verify connection strings match your setup

### Memory Issues

- Reduce `INSERT_COUNT` or `CONCURRENCY` for large datasets
- Increase JVM heap: `java -Xmx2g -jar ...`

### Transaction Failures

- Check database logs: `docker-compose logs postgres` or `docker-compose logs mongodb`
- Ensure sufficient disk space
- Verify foreign key constraints (Scenario 2)

## Performance Considerations

- **Virtual Threads**: Allows efficient handling of high concurrency without thread starvation
- **Batch Operations**: Item inserts are batched for efficiency
- **Connection Pooling**: Can be added to optimize database connections
- **Indexing**: Indexes are created per scenario specification

## Future Enhancements

- Connection pooling (HikariCP)
- Warm-up runs before metrics collection
- Memory profiling
- Network latency simulation
- Parameterized test data sizes
