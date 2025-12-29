# Benchmark Implementation Todo List

This is a sequential todo list for implementing the MongoDB 8 vs PostgreSQL 18 benchmark based on SPEC.md.

## Project Setup
- [ ] 1. Set up Gradle project structure - Create Java 25 project with Gradle, add dependencies for MongoDB Java driver, PostgreSQL JDBC, and any required libraries for virtual threads, CSV handling, and metrics collection.

## Data Models and Connections
- [ ] 2. Define data models - Create Order and Item Java classes with fields matching the spec (order_id, customer_id, etc.), including serialization support for both MongoDB (BSON) and PostgreSQL (JSONB).
- [ ] 3. Implement database connection utilities - Create connection classes for MongoDB and PostgreSQL with proper connection string parsing and error handling.

## Scenario 1 Implementation (Embedded Storage)
- [ ] 4. Implement Scenario 1 (Embedded) - MongoDB operations - Create classes for insert, update-modify, update-add, query, and delete operations using embedded documents with atomic total_amount calculations.
- [ ] 5. Implement Scenario 1 (Embedded) - PostgreSQL operations - Create classes for insert, update-modify, update-add, query, and delete operations using JSONB storage with atomic total_amount calculations.

## Scenario 2 Implementation (Multi-Document/Table)
- [ ] 6. Implement Scenario 2 (Multi-Document/Table) - MongoDB operations - Create classes for transactional insert, update-modify, update-add, query (with aggregation), and delete operations across separate collections.
- [ ] 7. Implement Scenario 2 (Multi-Document/Table) - PostgreSQL operations - Create classes for transactional insert, update-modify, update-add, query (with joins), and delete operations across separate tables.

## Core Framework
- [ ] 8. Implement virtual thread concurrency framework - Create executor service using Java 25 virtual threads with configurable concurrency limits for all operation types.
- [ ] 9. Implement operation execution flow - Create the sequential execution logic: inserts first (sequential IDs), then random selection for update-modify, update-add, query, and delete operations.
- [ ] 10. Implement metrics collection - Add timing and throughput measurement for each operation type, calculating p50, p75, p99 latency percentiles.

## Output and Aggregation
- [ ] 11. Implement CSV output for measurement mode - Create CSV writer with ";" delimiter, outputting metrics in "operation_name_metric_name_measure_unit|value" format.
- [ ] 12. Implement aggregation mode - Create logic to read multiple CSV files and generate aggregated output with headers and combined values.

## Command-Line and Automation
- [ ] 13. Implement command-line argument parsing - Add parsing for all --mode, --scenario, --database, --concurrency, operation counts, --connection-string, --output-file, and --input-files parameters with validation.
- [ ] 14. Create Docker setup scripts - Write scripts for provisioning/cleanup of PostgreSQL 18 and MongoDB 8 containers with proper volume management.
- [ ] 15. Create automated shell script - Implement the 8-step sequential script: cleanup, PostgreSQL provisioning/benchmark/cleanup, MongoDB provisioning/benchmark/cleanup, aggregation.

## Testing and Validation
- [ ] 16. Add comprehensive error handling and validation - Implement post-operation checks, total_amount validation, retry logic with exponential backoff and jitter on exceptions (exit program if all retries fail), and graceful failure handling throughout.
- [ ] 17. Test individual components - Unit test each operation type, scenario, and database combination.
- [ ] 18. Integration testing - Test full benchmark runs with both databases and aggregation.
- [ ] 19. Performance validation - Run tests to ensure metrics are collected correctly and outputs match expected formats.

## Finalization
- [ ] 20. Documentation and final review - Update any implementation docs and validate against SPEC.md requirements.