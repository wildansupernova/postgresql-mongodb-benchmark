#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/build"
JAR_FILE="$BUILD_DIR/libs/postgresql-mongodb-benchmark-1.0.0.jar"

SCENARIO=${SCENARIO:-2}
CONCURRENCY=${CONCURRENCY:-100}
INSERT_COUNT=${INSERT_COUNT:-10000}
UPDATE_MODIFY_COUNT=${UPDATE_MODIFY_COUNT:-10000}
UPDATE_ADD_COUNT=${UPDATE_ADD_COUNT:-10000}
QUERY_COUNT=${QUERY_COUNT:-10000}
DELETE_COUNT=${DELETE_COUNT:-10000}
PREFIX=${PREFIX:-test1}
OUTPUT_DIR="$SCRIPT_DIR/benchmark_results"
mkdir -p "$OUTPUT_DIR"

echo "=========================================="
echo "MongoDB vs PostgreSQL Benchmark"
echo "=========================================="
echo "Scenario: $SCENARIO"
echo "Concurrency: $CONCURRENCY"
echo "Insert Count: $INSERT_COUNT"
echo "Update Modify Count: $UPDATE_MODIFY_COUNT"
echo "Update Add Count: $UPDATE_ADD_COUNT"
echo "Query Count: $QUERY_COUNT"
echo "Delete Count: $DELETE_COUNT"
echo "Prefix: $PREFIX"
echo "=========================================="

cleanup_containers() {
    echo "Cleaning up containers and volumes..."
    docker-compose -f "$SCRIPT_DIR/docker-compose.yml" down -v
}

run_postgresql_benchmark() {
    echo ""
    echo "========== PostgreSQL Benchmark =========="
    
    echo "Starting PostgreSQL..."
    docker-compose -f "$SCRIPT_DIR/docker-compose.yml" up -d postgres
    sleep 30
    
    echo "Running benchmark for PostgreSQL..."
    java -jar "$JAR_FILE" \
        --mode measurement \
        --scenario "$SCENARIO" \
        --database postgresql \
        --concurrency "$CONCURRENCY" \
        --insert-count "$INSERT_COUNT" \
        --update-modify-count "$UPDATE_MODIFY_COUNT" \
        --update-add-count "$UPDATE_ADD_COUNT" \
        --query-count "$QUERY_COUNT" \
        --delete-count "$DELETE_COUNT" \
        --connection-string "jdbc:postgresql://localhost:5432/benchmark_db?user=benchmark&password=benchmark_password" \
        --output-file "$OUTPUT_DIR/${PREFIX}_${SCENARIO}_postgres_results.csv"
    
    echo "PostgreSQL benchmark completed!"
    
    echo "Stopping PostgreSQL..."
    docker-compose -f "$SCRIPT_DIR/docker-compose.yml" stop postgres
}

run_mongodb_benchmark() {
    echo ""
    echo "========== MongoDB Benchmark =========="
    
    echo "Starting MongoDB..."
    docker-compose -f "$SCRIPT_DIR/docker-compose.yml" up -d mongodb
    sleep 10
    
    echo "Initializing MongoDB replica set..."
    docker exec benchmark-mongodb mongosh --eval "rs.initiate({_id: 'rs0', members: [{_id: 0, host: 'localhost:27017'}]})" 2>/dev/null || true
    sleep 10
    
    echo "Running benchmark for MongoDB..."
    java -jar "$JAR_FILE" \
        --mode measurement \
        --scenario "$SCENARIO" \
        --database mongodb \
        --concurrency "$CONCURRENCY" \
        --insert-count "$INSERT_COUNT" \
        --update-modify-count "$UPDATE_MODIFY_COUNT" \
        --update-add-count "$UPDATE_ADD_COUNT" \
        --query-count "$QUERY_COUNT" \
        --delete-count "$DELETE_COUNT" \
        --connection-string "mongodb://localhost:27017/benchmark_db" \
        --output-file "$OUTPUT_DIR/${PREFIX}_${SCENARIO}_mongodb_results.csv"
    
    echo "MongoDB benchmark completed!"
    
    echo "Stopping MongoDB..."
    docker-compose -f "$SCRIPT_DIR/docker-compose.yml" stop mongodb
}

run_aggregation() {
    echo ""
    echo "========== Aggregating Results =========="
    
    if [[ ! -f "$OUTPUT_DIR/${PREFIX}_${SCENARIO}_postgres_results.csv" ]] || [[ ! -f "$OUTPUT_DIR/${PREFIX}_${SCENARIO}_mongodb_results.csv" ]]; then
        echo "Error: Benchmark result files not found!"
        exit 1
    fi
    
    java -jar "$JAR_FILE" \
        --mode aggregation \
        --input-files "$OUTPUT_DIR/${PREFIX}_${SCENARIO}_postgres_results.csv,$OUTPUT_DIR/${PREFIX}_${SCENARIO}_mongodb_results.csv" \
        --output-file "$OUTPUT_DIR/${PREFIX}_${SCENARIO}_aggregated_results.csv"
    
    echo "Aggregation completed!"
}

build_project() {
    echo "Building project..."
    cd "$SCRIPT_DIR"
    ./gradlew clean build -x test
    echo "Build completed!"
}

build_project

cleanup_containers
run_mongodb_benchmark
cleanup_containers
run_postgresql_benchmark
run_aggregation
cleanup_containers

echo ""
echo "=========================================="
echo "Benchmark Complete!"
echo "Results saved to: $OUTPUT_DIR"
echo "=========================================="
