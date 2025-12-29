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
  - Query: Join order with items, validate total_amount consistency
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
- Error handling: Graceful failure and recovery mechanisms

## Implementation Notes
- Use official drivers: MongoDB Java driver, PostgreSQL JDBC driver
- Benchmark framework: Custom Java 25 application with virtual threads for concurrency, using Gradle for dependency management
- Environment: Docker containers for MongoDB 8 and PostgreSQL 18 on identical hardware (2-core CPU, 2 GB RAM, SSD storage)
- Operation Execution Flow:
  - Inserts: Generate sequential order IDs from 0 to (insert_count-1), perform insertions asynchronously using virtual threads bounded by concurrency level
  - Update-Modify: Execute update-modify-count operations asynchronously with virtual threads bounded by concurrency level, randomly selecting order IDs from 0 to (insert_count-1)
  - Update-Add: Execute update-add-count operations asynchronously with virtual threads bounded by concurrency level, randomly selecting order IDs from 0 to (insert_count-1)
  - Queries: Execute query-count operations asynchronously with virtual threads bounded by concurrency level, randomly selecting order IDs from 0 to (insert_count-1)
  - Deletes: Execute delete-count operations asynchronously with virtual threads bounded by concurrency level, randomly selecting order IDs from 0 to (insert_count-1)
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