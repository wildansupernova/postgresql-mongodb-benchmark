package com.mrscrape.benchmark.db.scenario1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrscrape.benchmark.db.DatabaseOperations;
import com.mrscrape.benchmark.db.PostgresConnection;
import com.mrscrape.benchmark.db.RetryUtil;
import com.mrscrape.benchmark.model.Item;
import com.mrscrape.benchmark.model.Order;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class PostgresJsonbOps implements DatabaseOperations {
    private final PostgresConnection postgresConnection;
    private final ObjectMapper objectMapper;
    private static final String TABLE_NAME = "orders";

    public PostgresJsonbOps(PostgresConnection postgresConnection) {
        this.postgresConnection = postgresConnection;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    @Override
    public void setup() throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            try (Connection conn = postgresConnection.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
                stmt.execute("CREATE TABLE " + TABLE_NAME + " (" +
                        "order_id VARCHAR PRIMARY KEY, " +
                        "customer_id VARCHAR, " +
                        "order_date TIMESTAMP, " +
                        "total_amount BIGINT, " +
                        "status VARCHAR, " +
                        "items JSONB)");
            }
        }, "PostgresJsonbOps.setup");
    }

    @Override
    public void teardown() throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            try (Connection conn = postgresConnection.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
            }
        }, "PostgresJsonbOps.teardown");
    }

    @Override
    public void insert(Order order) throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            try (Connection conn = postgresConnection.getConnection()) {
                
                try {
                    List<java.util.Map<String, Object>> itemsList = new ArrayList<>();
                    for (Item item : order.getItems()) {
                        java.util.Map<String, Object> itemMap = new java.util.LinkedHashMap<>();
                        itemMap.put("item_id", item.getItemId());
                        itemMap.put("product_id", item.getProductId());
                        itemMap.put("name", item.getName());
                        itemMap.put("price", item.getPrice());
                        itemMap.put("quantity", item.getQuantity());
                        itemsList.add(itemMap);
                    }
                    
                    order.recalculateTotalAmount();
                    String itemsJson = objectMapper.writeValueAsString(itemsList);
                    
                    String sql = "INSERT INTO " + TABLE_NAME + " (order_id, customer_id, order_date, total_amount, status, items) " +
                            "VALUES (?, ?, ?, ?, ?, ?::jsonb)";
                    
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, order.getOrderId());
                        pstmt.setString(2, order.getCustomerId());
                        pstmt.setTimestamp(3, Timestamp.from(order.getOrderDate()));
                        pstmt.setLong(4, order.getTotalAmount());
                        pstmt.setString(5, order.getStatus());
                        pstmt.setString(6, itemsJson);
                        pstmt.executeUpdate();
                    }
                } catch (Exception e) {
                    if (isDuplicateKeyException(e)) {
                        return;
                    }
                    throw e;
                }
            }
        }, "PostgresJsonbOps.insert");
    }

    private boolean isDuplicateKeyException(Exception e) {
        if (e.getClass().getName().contains("DuplicateKeyException")) {
            return true;
        }
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        message = message.toLowerCase();
        return message.contains("duplicate") || message.contains("unique constraint");
    }

    @Override
    public void updateModify(String orderId) throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            try (Connection conn = postgresConnection.getConnection()) {
                
                Order order = query(orderId);
                List<Item> items = order.getItems();
                ThreadLocalRandom rand = ThreadLocalRandom.current();
                
                for (Item item : items) {
                    item.setPrice(item.getPrice() + (rand.nextInt(100) + 1));
                    item.setQuantity(Math.max(1, item.getQuantity() + (rand.nextInt(5) - 2)));
                }
                
                List<java.util.Map<String, Object>> itemsList = new ArrayList<>();
                for (Item item : items) {
                    java.util.Map<String, Object> itemMap = new java.util.LinkedHashMap<>();
                    itemMap.put("item_id", item.getItemId());
                    itemMap.put("product_id", item.getProductId());
                    itemMap.put("name", item.getName());
                    itemMap.put("price", item.getPrice());
                    itemMap.put("quantity", item.getQuantity());
                    itemsList.add(itemMap);
                }
                
                order.recalculateTotalAmount();
                String itemsJson = objectMapper.writeValueAsString(itemsList);
                
                String sql = "UPDATE " + TABLE_NAME + " SET total_amount = ?, items = ?::jsonb WHERE order_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setLong(1, order.getTotalAmount());
                    pstmt.setString(2, itemsJson);
                    pstmt.setString(3, orderId);
                    pstmt.executeUpdate();
                }
            }
        }, "PostgresJsonbOps.updateModify");
    }

    @Override
    public void updateAdd(String orderId) throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            try (Connection conn = postgresConnection.getConnection()) {
                
                Order order = query(orderId);
                List<Item> items = order.getItems();
                
                int currentSize = items.size();
                ThreadLocalRandom rand = ThreadLocalRandom.current();
                for (int i = 0; i < 5; i++) {
                    Item newItem = new Item(
                            orderId + "_item_" + (currentSize + i + 1),
                            orderId,
                            "product_" + (currentSize + i + 1),
                            "New Product " + (currentSize + i + 1),
                            (long)(rand.nextDouble() * 900) + 100,
                            rand.nextInt(10) + 1
                    );
                    items.add(newItem);
                }
                
                List<java.util.Map<String, Object>> itemsList = new ArrayList<>();
                for (Item item : items) {
                    java.util.Map<String, Object> itemMap = new java.util.LinkedHashMap<>();
                    itemMap.put("item_id", item.getItemId());
                    itemMap.put("product_id", item.getProductId());
                    itemMap.put("name", item.getName());
                    itemMap.put("price", item.getPrice());
                    itemMap.put("quantity", item.getQuantity());
                    itemsList.add(itemMap);
                }
                
                order.recalculateTotalAmount();
                String itemsJson = objectMapper.writeValueAsString(itemsList);
                
                String sql = "UPDATE " + TABLE_NAME + " SET total_amount = ?, items = ?::jsonb WHERE order_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setLong(1, order.getTotalAmount());
                    pstmt.setString(2, itemsJson);
                    pstmt.setString(3, orderId);
                    pstmt.executeUpdate();
                }
            }
        }, "PostgresJsonbOps.updateAdd");
    }

    @Override
    public Order query(String orderId) throws Exception {
        return RetryUtil.executeWithRetry(() -> {
            try (Connection conn = postgresConnection.getConnection()) {
                
                String sql = "SELECT * FROM " + TABLE_NAME + " WHERE order_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, orderId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            Order order = new Order(
                                    rs.getString("order_id"),
                                    rs.getString("customer_id"),
                                    rs.getTimestamp("order_date").toInstant(),
                                    rs.getString("status")
                            );
                            order.setTotalAmount(rs.getLong("total_amount"));
                            
                            String itemsJson = rs.getString("items");
                            if (itemsJson != null && !itemsJson.isEmpty()) {
                                List<java.util.Map<String, Object>> itemsList = objectMapper.readValue(
                                        itemsJson,
                                        objectMapper.getTypeFactory().constructCollectionType(List.class, java.util.Map.class)
                                );
                                
                                for (java.util.Map<String, Object> itemMap : itemsList) {
                                    Item item = new Item(
                                            (String) itemMap.get("item_id"),
                                            orderId,
                                            (String) itemMap.get("product_id"),
                                            (String) itemMap.get("name"),
                                            ((Number) itemMap.get("price")).longValue(),
                                            ((Number) itemMap.get("quantity")).longValue()
                                    );
                                    order.addItem(item);
                                }
                            }
                            
                            return order;
                        }
                    }
                }
                
                throw new Exception("Order not found: " + orderId);
            }
        }, "PostgresJsonbOps.query");
    }

    @Override
    public void delete(String orderId) throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            try (Connection conn = postgresConnection.getConnection()) {
                
                String sql = "DELETE FROM " + TABLE_NAME + " WHERE order_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, orderId);
                    pstmt.executeUpdate();
                }
            }
        }, "PostgresJsonbOps.delete");
    }

    @Override
    public void validateTotalAmount(String orderId) throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            try (Connection conn = postgresConnection.getConnection()) {
                
                String sql = "SELECT total_amount, items FROM " + TABLE_NAME + " WHERE order_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, orderId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            long storedTotal = rs.getLong("total_amount");
                            String itemsJson = rs.getString("items");
                            
                            List<java.util.Map<String, Object>> itemsList = objectMapper.readValue(
                                    itemsJson,
                                    objectMapper.getTypeFactory().constructCollectionType(List.class, java.util.Map.class)
                            );
                            
                            long calculatedTotal = itemsList.stream()
                                    .mapToLong(item -> ((Number) item.get("price")).longValue() * 
                                            ((Number) item.get("quantity")).longValue())
                                    .sum();
                            
                            if (calculatedTotal != storedTotal) {
                                throw new Exception("Total amount mismatch for order " + orderId + 
                                        ": calculated=" + calculatedTotal + ", stored=" + storedTotal);
                            }
                            return;
                        }
                    }
                }
                
                throw new Exception("Order not found for validation: " + orderId);
            }
        }, "PostgresJsonbOps.validateTotalAmount");
    }
}
