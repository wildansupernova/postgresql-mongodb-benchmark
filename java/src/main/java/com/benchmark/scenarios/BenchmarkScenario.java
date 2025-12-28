package com.benchmark.scenarios;

import com.benchmark.models.Item;
import com.benchmark.models.Order;

import java.util.List;
import java.util.UUID;

public interface BenchmarkScenario {
    void setup();
    void cleanup();
    
    // Write operations
    UUID insert(Order order);
    void append(UUID orderId, List<Item> items);
    void update(UUID orderId, UUID itemId, Item updatedItem);
    void delete(UUID orderId, UUID itemId);
    void batchInsert(List<Order> orders);
    
    // Read operations
    Order fetchOrder(UUID orderId);
    Order fetchFiltered(UUID orderId, String filter);
    long count(UUID orderId);
    double aggregate(UUID orderId);
    List<Order> batchFetch(List<UUID> orderIds);
}
