package com.mrscrape.benchmark.db.scenario2;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class PostgresMultiTableOps implements DatabaseOperations {
    private final PostgresConnection postgresConnection;
    private static final String ORDERS_TABLE = "orders";
    private static final String ITEMS_TABLE = "items";

    public PostgresMultiTableOps(PostgresConnection postgresConnection) {
        this.postgresConnection = postgresConnection;
    }

    @Override
    public void setup() throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            try (Connection conn = postgresConnection.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + ITEMS_TABLE);
                stmt.execute("DROP TABLE IF EXISTS " + ORDERS_TABLE);
                
                stmt.execute("CREATE TABLE " + ORDERS_TABLE + " (" +
                        "order_id VARCHAR PRIMARY KEY, " +
                        "customer_id VARCHAR, " +
                        "order_date TIMESTAMP, " +
                        "total_amount BIGINT, " +
                        "status VARCHAR)");
                
                stmt.execute("CREATE TABLE " + ITEMS_TABLE + " (" +
                        "item_id VARCHAR PRIMARY KEY, " +
                        "order_id VARCHAR, " +
                        "product_id VARCHAR, " +
                        "name VARCHAR, " +
                        "price BIGINT, " +
                        "quantity BIGINT)");
                
                stmt.execute("CREATE INDEX idx_items_order_id ON " + ITEMS_TABLE + "(order_id)");
            }
        }, "PostgresMultiTableOps.setup");
    }

    @Override
    public void teardown() throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            try (Connection conn = postgresConnection.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + ITEMS_TABLE);
                stmt.execute("DROP TABLE IF EXISTS " + ORDERS_TABLE);
            }
        }, "PostgresMultiTableOps.teardown");
    }

    @Override
    public void insert(Order order) throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            try (Connection conn = postgresConnection.getConnection()) {
                boolean autoCommit = conn.getAutoCommit();
                
                try {
                    conn.setAutoCommit(false);
                    
                    order.recalculateTotalAmount();
                    
                    String orderSql = "INSERT INTO " + ORDERS_TABLE + 
                            " (order_id, customer_id, order_date, total_amount, status) " +
                            "VALUES (?, ?, ?, ?, ?)";
                    
                    try (PreparedStatement pstmt = conn.prepareStatement(orderSql)) {
                        pstmt.setString(1, order.getOrderId());
                        pstmt.setString(2, order.getCustomerId());
                        pstmt.setTimestamp(3, Timestamp.from(order.getOrderDate()));
                        pstmt.setLong(4, order.getTotalAmount());
                        pstmt.setString(5, order.getStatus());
                        pstmt.executeUpdate();
                    }
                    
                    String itemSql = "INSERT INTO " + ITEMS_TABLE + 
                            " (item_id, order_id, product_id, name, price, quantity) " +
                            "VALUES (?, ?, ?, ?, ?, ?)";
                    
                    try (PreparedStatement pstmt = conn.prepareStatement(itemSql)) {
                        for (Item item : order.getItems()) {
                            pstmt.setString(1, item.getItemId());
                            pstmt.setString(2, order.getOrderId());
                            pstmt.setString(3, item.getProductId());
                            pstmt.setString(4, item.getName());
                            pstmt.setLong(5, item.getPrice());
                            pstmt.setLong(6, item.getQuantity());
                            pstmt.addBatch();
                        }
                        pstmt.executeBatch();
                    }
                    
                    conn.commit();
                } catch (Exception e) {
                    if (isDuplicateKeyException(e)) {
                        conn.commit();
                        return;
                    }
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(autoCommit);
                }
            }
        }, "PostgresMultiTableOps.insert");
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
                boolean autoCommit = conn.getAutoCommit();
                
                try {
                    conn.setAutoCommit(false);
                    
                    // Get current items directly (no JOIN needed for update)
                    String selectItemsSql = "SELECT item_id, price, quantity FROM " + ITEMS_TABLE + " WHERE order_id = ?";
                    List<Item> items = new ArrayList<>();
                    
                    try (PreparedStatement pstmt = conn.prepareStatement(selectItemsSql)) {
                        pstmt.setString(1, orderId);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            while (rs.next()) {
                                Item item = new Item();
                                item.setItemId(rs.getString("item_id"));
                                item.setOrderId(orderId);
                                item.setPrice(rs.getLong("price"));
                                item.setQuantity(rs.getLong("quantity"));
                                items.add(item);
                            }
                        }
                    }
                    
                    if (items.isEmpty()) {
                        throw new Exception("No items found for order: " + orderId);
                    }
                    
                    // Update items with new random values
                    ThreadLocalRandom rand = ThreadLocalRandom.current();
                    String updateItemSql = "UPDATE " + ITEMS_TABLE + 
                            " SET price = ?, quantity = ? WHERE item_id = ?";
                    
                    long newTotal = 0;
                    try (PreparedStatement pstmt = conn.prepareStatement(updateItemSql)) {
                        for (Item item : items) {
                            long newPrice = item.getPrice() + (rand.nextInt(100) + 1);
                            long newQuantity = Math.max(1, item.getQuantity() + (rand.nextInt(5) - 2));
                            
                            item.setPrice(newPrice);
                            item.setQuantity(newQuantity);
                            newTotal += newPrice * newQuantity;
                            
                            pstmt.setLong(1, newPrice);
                            pstmt.setLong(2, newQuantity);
                            pstmt.setString(3, item.getItemId());
                            pstmt.addBatch();
                        }
                        pstmt.executeBatch();
                    }
                    
                    // Update order total
                    String updateOrderSql = "UPDATE " + ORDERS_TABLE + " SET total_amount = ? WHERE order_id = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(updateOrderSql)) {
                        pstmt.setLong(1, newTotal);
                        pstmt.setString(2, orderId);
                        pstmt.executeUpdate();
                    }
                    
                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(autoCommit);
                }
            }
        }, "PostgresMultiTableOps.updateModify");
    }

    @Override
    public void updateAdd(String orderId) throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            try (Connection conn = postgresConnection.getConnection()) {
                boolean autoCommit = conn.getAutoCommit();
                
                try {
                    conn.setAutoCommit(false);
                    
                    // Get current items count and calculate current total (no JOIN needed)
                    String countItemsSql = "SELECT COUNT(*) as item_count FROM " + ITEMS_TABLE + " WHERE order_id = ?";
                    String sumItemsSql = "SELECT COALESCE(SUM(price * quantity), 0) as current_total FROM " + ITEMS_TABLE + " WHERE order_id = ?";
                    
                    int currentSize = 0;
                    long currentTotal = 0;
                    
                    try (PreparedStatement pstmt = conn.prepareStatement(countItemsSql)) {
                        pstmt.setString(1, orderId);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            if (rs.next()) {
                                currentSize = rs.getInt("item_count");
                            }
                        }
                    }
                    
                    try (PreparedStatement pstmt = conn.prepareStatement(sumItemsSql)) {
                        pstmt.setString(1, orderId);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            if (rs.next()) {
                                currentTotal = rs.getLong("current_total");
                            }
                        }
                    }
                    
                    // Add 5 new items
                    ThreadLocalRandom rand = ThreadLocalRandom.current();
                    String itemSql = "INSERT INTO " + ITEMS_TABLE + 
                            " (item_id, order_id, product_id, name, price, quantity) " +
                            "VALUES (?, ?, ?, ?, ?, ?)";
                    
                    long newItemsTotal = 0;
                    try (PreparedStatement pstmt = conn.prepareStatement(itemSql)) {
                        for (int i = 0; i < 5; i++) {
                            String itemId = orderId + "_item_" + (currentSize + i + 1);
                            long price = (long)(rand.nextDouble() * 900) + 100;
                            long quantity = rand.nextInt(10) + 1;
                            
                            pstmt.setString(1, itemId);
                            pstmt.setString(2, orderId);
                            pstmt.setString(3, "product_" + (currentSize + i + 1));
                            pstmt.setString(4, "New Product " + (currentSize + i + 1));
                            pstmt.setLong(5, price);
                            pstmt.setLong(6, quantity);
                            pstmt.addBatch();
                            
                            newItemsTotal += price * quantity;
                        }
                        pstmt.executeBatch();
                    }
                    
                    // Update order total
                    long newTotal = currentTotal + newItemsTotal;
                    String updateOrderSql = "UPDATE " + ORDERS_TABLE + " SET total_amount = ? WHERE order_id = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(updateOrderSql)) {
                        pstmt.setLong(1, newTotal);
                        pstmt.setString(2, orderId);
                        pstmt.executeUpdate();
                    }
                    
                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(autoCommit);
                }
            }
        }, "PostgresMultiTableOps.updateAdd");
    }


    @Override
    public Order query(String orderId) throws Exception {
        return RetryUtil.executeWithRetry(() -> {
            try (Connection conn = postgresConnection.getConnection()) {
                
                // Use SQL JOIN to combine order and items in a single query
                String joinSql = "SELECT o.order_id, o.customer_id, o.order_date, o.total_amount, o.status, " +
                               "i.item_id, i.product_id, i.name, i.price, i.quantity " +
                               "FROM " + ORDERS_TABLE + " o " +
                               "LEFT JOIN " + ITEMS_TABLE + " i ON o.order_id = i.order_id " +
                               "WHERE o.order_id = ? " +
                               "ORDER BY i.item_id";
                
                Order order = null;
                
                try (PreparedStatement pstmt = conn.prepareStatement(joinSql)) {
                    pstmt.setString(1, orderId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        boolean firstRow = true;
                        while (rs.next()) {
                            if (firstRow) {
                                // Create order from first row
                                order = new Order(
                                        rs.getString("order_id"),
                                        rs.getString("customer_id"),
                                        rs.getTimestamp("order_date").toInstant(),
                                        rs.getString("status")
                                );
                                order.setTotalAmount(rs.getLong("total_amount"));
                                firstRow = false;
                            }
                            
                            // Add item if it exists (LEFT JOIN may return NULL for items)
                            String itemId = rs.getString("item_id");
                            if (itemId != null) {
                                Item item = new Item(
                                        itemId,
                                        orderId,
                                        rs.getString("product_id"),
                                        rs.getString("name"),
                                        rs.getLong("price"),
                                        rs.getLong("quantity")
                                );
                                order.addItem(item);
                            }
                        }
                        
                        if (order == null) {
                            throw new Exception("Order not found: " + orderId);
                        }
                    }
                }
                
                return order;
            }
        }, "PostgresMultiTableOps.query");
    }

    @Override
    public void delete(String orderId) throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            try (Connection conn = postgresConnection.getConnection()) {
                boolean autoCommit = conn.getAutoCommit();
                
                try {
                    conn.setAutoCommit(false);
                    
                    String deleteItemsSql = "DELETE FROM " + ITEMS_TABLE + " WHERE order_id = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(deleteItemsSql)) {
                        pstmt.setString(1, orderId);
                        pstmt.executeUpdate();
                    }
                    
                    String deleteOrderSql = "DELETE FROM " + ORDERS_TABLE + " WHERE order_id = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(deleteOrderSql)) {
                        pstmt.setString(1, orderId);
                        pstmt.executeUpdate();
                    }
                    
                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(autoCommit);
                }
            }
        }, "PostgresMultiTableOps.delete");
    }

    @Override
    public void validateTotalAmount(String orderId) throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            try (Connection conn = postgresConnection.getConnection()) {
                
                String sql = "SELECT o.total_amount, COALESCE(SUM(i.price * i.quantity), 0) as calculated_total " +
                        "FROM " + ORDERS_TABLE + " o " +
                        "LEFT JOIN " + ITEMS_TABLE + " i ON o.order_id = i.order_id " +
                        "WHERE o.order_id = ? " +
                        "GROUP BY o.order_id, o.total_amount";
                
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, orderId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            long storedTotal = rs.getLong("total_amount");
                            long calculatedTotal = rs.getLong("calculated_total");
                            
                            if (calculatedTotal != storedTotal) {
                                throw new Exception("Total amount mismatch for order " + orderId + 
                                        ": calculated=" + calculatedTotal + ", stored=" + storedTotal);
                            }
                        } else {
                            throw new Exception("Order not found for validation: " + orderId);
                        }
                    }
                }
            }
        }, "PostgresMultiTableOps.validateTotalAmount");
    }
}
