#!/bin/bash
set -e

echo "Setting up PostgreSQL and MongoDB benchmark environment..."

# Make init scripts executable
chmod +x docker/init-mongo.sh

# Remove any existing containers (forced)
echo "Removing any existing containers..."
docker rm -f benchmark-mongo benchmark-postgres benchmark-mongo-init 2>/dev/null || true

# Start containers
echo "Starting containers..."
docker-compose up -d

# Wait for mongo-init to start and complete
echo "Waiting for MongoDB replica set initialization..."
sleep 5
docker start benchmark-mongo-init 2>/dev/null || true
sleep 20

# Wait for databases to be ready
echo "Waiting for databases to initialize..."
sleep 5

# Check MongoDB
echo "Checking MongoDB..."
docker exec benchmark-mongo mongosh --eval "rs.status()" || echo "MongoDB replica set initializing..."

# Check PostgreSQL
echo "Checking PostgreSQL..."
docker exec benchmark-postgres psql -U postgres -d benchmark_db -c "\dt" || echo "PostgreSQL initializing..."

echo "Setup complete! Databases are ready."
echo ""
echo "MongoDB URI: mongodb://localhost:27017/?replicaSet=rs0"
echo "PostgreSQL: localhost:5432/benchmark_db (user: postgres, password: postgres)"
