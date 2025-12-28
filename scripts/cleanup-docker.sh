#!/bin/bash

echo "Cleaning up benchmark environment..."

# Remove volumes and network
docker-compose down -v --remove-orphans

# Remove any dangling volumes
docker volume prune -f

echo "Cleanup complete!"
