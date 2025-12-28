# MongoDB vs PostgreSQL Relationship Benchmark

Comprehensive performance benchmark comparing MongoDB and PostgreSQL for one-to-many relationship handling patterns.

## Overview

This benchmark evaluates MongoDB and PostgreSQL performance across different architectural patterns:
- **Scenario 1**: Embedded Documents (MongoDB) vs JSONB (PostgreSQL)
- **Scenario 2**: Embedded Documents (MongoDB) vs Normalized Tables (PostgreSQL)
- **Scenario 3**: Multi-Collection with Transactions (MongoDB) vs JSONB (PostgreSQL)
- **Scenario 4**: Multi-Collection with Transactions (MongoDB) vs Normalized Tables (PostgreSQL)

## Quick Start

### Prerequisites
- Docker and Docker Compose
- Java 25
- 8GB RAM minimum

### Setup

1. **Start Databases**
```bash
chmod +x scripts/setup-docker.sh
./scripts/setup-docker.sh
```

2. **Run Benchmark**
```bash
chmod +x scripts/run-benchmark.sh
./scripts/run-benchmark.sh
```

3. **View Results**
Results are saved in `results/` directory with CSV and Markdown formats.

### Cleanup
```bash
chmod +x scripts/cleanup-docker.sh
./scripts/cleanup-docker.sh
```

- **Scales**: Data size (micro/small/medium/large)
- **Operations**: Number of operations to run
- **Concurrency**: Thread count for parallel execution
- **Database settings**: Connection parameters

### Example: Quick Test

```yaml
benchmark:
  scales: [micro]
  total_operations: 1000
  concurrency_levels: [1, 10]
```

## Project Structure

```
/docker          - Database containers
/java            - Java 25 implementation
  /src/main/java
    /com/benchmark
      /models     - Order and Item domain models
      /config     - Configuration management
      /testdata   - Data generators
      /metrics    - Performance collectors
      /scenarios  - 4 benchmark scenarios
/results         - Output reports (JSON, CSV, Markdown)
```

## Scenarios

1. **Embedded vs JSONB**: MongoDB embedded documents vs PostgreSQL JSONB
2. **Embedded vs Normalized**: MongoDB embedded vs PostgreSQL normalized tables
3. **Multi-Collection vs JSONB**: MongoDB multi-collection (transactions) vs PostgreSQL JSONB
4. **Multi-Collection vs Normalized**: Both using normalized schemas with transactions

## Operations Benchmarked

- **INSERT**: Create order with items
- **APPEND**: Add items to existing order
- **UPDATE**: Modify item in order
- **DELETE**: Remove item from order
- **FETCH_ORDER**: Retrieve order with all items
- **AGGREGATE**: Calculate order totals

## Results

Results are saved in `/results` directory:
- `results-YYYYMMDD-HHMMSS.json` - Machine-readable format
- `results-YYYYMMDD-HHMMSS.csv` - For Excel/analysis tools
- `results-YYYYMMDD-HHMMSS.md` - Human-readable tables

## Stopping Databases

```bash
cd docker
docker-compose down -v
```

## Troubleshooting

**MongoDB replica set not initialized?**
```bash
docker exec -it benchmark-mongo mongosh --eval "rs.status()"
```

**PostgreSQL connection refused?**
```bash
docker exec -it benchmark-postgres pg_isready -U postgres
```

**Clear all data and restart:**
```bash
cd docker
docker-compose down -v
docker-compose up -d
```
