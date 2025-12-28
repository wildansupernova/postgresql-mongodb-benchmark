#!/bin/bash
set -e

echo "Waiting for MongoDB to be ready..."
sleep 10

# Wait for replica set to be ready
until mongosh --eval "print('MongoDB is ready')" > /dev/null 2>&1; do
  sleep 2
done

echo "Creating indexes for benchmark scenarios..."

mongosh <<EOF
use benchmark_db;

// Scenario 1 & 2: Embedded documents collection
db.createCollection("orders_embedded");
db.orders_embedded.createIndex({ "customer_email": 1 });
db.orders_embedded.createIndex({ "status": 1 });
db.orders_embedded.createIndex({ "created_at": -1 });
db.orders_embedded.createIndex({ "items.status": 1 });
db.orders_embedded.createIndex({ "items.product_sku": 1 });

// Scenario 3 & 4: Normalized collections
db.createCollection("orders_normalized");
db.createCollection("items_normalized");

// Orders collection indexes
db.orders_normalized.createIndex({ "customer_email": 1 });
db.orders_normalized.createIndex({ "status": 1 });
db.orders_normalized.createIndex({ "created_at": -1 });

// Items collection indexes
db.items_normalized.createIndex({ "order_id": 1 });
db.items_normalized.createIndex({ "status": 1 });
db.items_normalized.createIndex({ "product_sku": 1 });
db.items_normalized.createIndex({ "order_id": 1, "status": 1 });

print("MongoDB indexes created successfully");
EOF

echo "MongoDB initialization complete"
