#!/bin/bash
set -e

echo "Running MongoDB vs PostgreSQL Benchmark..."

# Clean up previous environment
echo "Cleaning up previous environment..."
./scripts/cleanup-docker.sh

# Setup fresh environment
echo "Setting up fresh environment..."
./scripts/setup-docker.sh

# Wait for databases to be ready
echo "Waiting for databases to stabilize..."
sleep 5

# Check if Java 25 is available
if ! java --version 2>&1 | grep -q "25"; then
    echo "Error: Java 25 is required but not found"
    echo "Current Java version:"
    java --version
    exit 1
fi

# Navigate to Java project
cd java

# Run the benchmark
echo "Building and running benchmark..."
./gradlew run --console=plain

echo "Benchmark completed! Check the results directory for output."
