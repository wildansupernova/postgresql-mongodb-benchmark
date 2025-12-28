package com.benchmark.scenarios.scenario2;

import com.benchmark.database.PostgreSQLConnection;
import com.benchmark.models.Item;
import com.benchmark.models.ItemStatus;
import com.benchmark.models.Order;
import com.benchmark.models.OrderStatus;
import com.benchmark.scenarios.BenchmarkScenario;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class PostgreSQLNormalizedScenario implements BenchmarkScenario {
    private final PostgreSQLConnection connection;
    private final ObjectMapper objectMapper;

    public PostgreSQLNormalizedScenario(PostgreSQLConnection connection) {
        this.connection = connection;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void setup() {
        try (Connection conn = connection.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("TRUNCATE TABLE orders_normalized CASCADE");
                stmt.execute("TRUNCATE TABLE items_normalized CASCADE");
            }
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Setup failed", e);
        }
    }

    @Override
    public void cleanup() {
        try (Connection conn = connection.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("TRUNCATE TABLE orders_normalized CASCADE");
                stmt.execute("TRUNCATE TABLE items_normalized CASCADE");
            }
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Cleanup failed", e);
        }
    }

    @Override
    public UUID insert(Order order) {
        String orderSql = "INSERT INTO orders_normalized (id, customer_name, customer_email, status, created_at, updated_at, metadata) " +
                          "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)";
        String itemSql = "INSERT INTO items_normalized (id, order_id, product_name, product_sku, quantity, unit_price, amount, status, tags, created_at) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = connection.getConnection()) {
            conn.setAutoCommit(false);
            
            try (PreparedStatement orderPs = conn.prepareStatement(orderSql);
                 PreparedStatement itemPs = conn.prepareStatement(itemSql)) {
                
                orderPs.setObject(1, order.getId());
                orderPs.setString(2, order.getCustomerName());
                orderPs.setString(3, order.getCustomerEmail());
                orderPs.setString(4, order.getStatus().name());
                orderPs.setTimestamp(5, Timestamp.valueOf(order.getCreatedAt()));
                orderPs.setTimestamp(6, Timestamp.valueOf(order.getUpdatedAt()));
                orderPs.setString(7, objectMapper.writeValueAsString(order.getMetadata()));
                orderPs.executeUpdate();
                
                for (Item item : order.getItems()) {
                    itemPs.setObject(1, item.getId());
                    itemPs.setObject(2, order.getId());
                    itemPs.setString(3, item.getProductName());
                    itemPs.setString(4, item.getProductSku());
                    itemPs.setInt(5, item.getQuantity());
                    itemPs.setBigDecimal(6, item.getUnitPrice());
                    itemPs.setBigDecimal(7, item.getAmount());
                    itemPs.setString(8, item.getStatus().name());
                    itemPs.setArray(9, conn.createArrayOf("text", item.getTags().toArray()));
                    itemPs.setTimestamp(10, Timestamp.valueOf(item.getCreatedAt()));
                    itemPs.addBatch();
                }
                itemPs.executeBatch();
                
                conn.commit();
                return order.getId();
            } catch (SQLException | JsonProcessingException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Insert failed", e);
        }
    }

    @Override
    public void append(UUID orderId, List<Item> items) {
        String sql = "INSERT INTO items_normalized (id, order_id, product_name, product_sku, quantity, unit_price, amount, status, tags, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = connection.getConnection()) {
            conn.setAutoCommit(false);
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Item item : items) {
                    ps.setObject(1, item.getId());
                    ps.setObject(2, orderId);
                    ps.setString(3, item.getProductName());
                    ps.setString(4, item.getProductSku());
                    ps.setInt(5, item.getQuantity());
                    ps.setBigDecimal(6, item.getUnitPrice());
                    ps.setBigDecimal(7, item.getAmount());
                    ps.setString(8, item.getStatus().name());
                    ps.setArray(9, conn.createArrayOf("text", item.getTags().toArray()));
                    ps.setTimestamp(10, Timestamp.valueOf(item.getCreatedAt()));
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Append failed", e);
        }
    }

    @Override
    public void update(UUID orderId, UUID itemId, Item updatedItem) {
        String sql = "UPDATE items_normalized SET product_name = ?, quantity = ?, unit_price = ?, amount = ?, status = ? " +
                     "WHERE id = ? AND order_id = ?";
        
        try (Connection conn = connection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, updatedItem.getProductName());
            ps.setInt(2, updatedItem.getQuantity());
            ps.setBigDecimal(3, updatedItem.getUnitPrice());
            ps.setBigDecimal(4, updatedItem.getAmount());
            ps.setString(5, updatedItem.getStatus().name());
            ps.setObject(6, itemId);
            ps.setObject(7, orderId);
            
            ps.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Update failed", e);
        }
    }

    @Override
    public void delete(UUID orderId, UUID itemId) {
        String sql = "DELETE FROM items_normalized WHERE id = ? AND order_id = ?";
        
        try (Connection conn = connection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setObject(1, itemId);
            ps.setObject(2, orderId);
            
            ps.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Delete failed", e);
        }
    }

    @Override
    public void batchInsert(List<Order> orders) {
        String orderSql = "INSERT INTO orders_normalized (id, customer_name, customer_email, status, created_at, updated_at, metadata) " +
                          "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)";
        String itemSql = "INSERT INTO items_normalized (id, order_id, product_name, product_sku, quantity, unit_price, amount, status, tags, created_at) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = connection.getConnection()) {
            conn.setAutoCommit(false);
            
            try (PreparedStatement orderPs = conn.prepareStatement(orderSql);
                 PreparedStatement itemPs = conn.prepareStatement(itemSql)) {
                
                for (Order order : orders) {
                    orderPs.setObject(1, order.getId());
                    orderPs.setString(2, order.getCustomerName());
                    orderPs.setString(3, order.getCustomerEmail());
                    orderPs.setString(4, order.getStatus().name());
                    orderPs.setTimestamp(5, Timestamp.valueOf(order.getCreatedAt()));
                    orderPs.setTimestamp(6, Timestamp.valueOf(order.getUpdatedAt()));
                    orderPs.setString(7, objectMapper.writeValueAsString(order.getMetadata()));
                    orderPs.addBatch();
                    
                    for (Item item : order.getItems()) {
                        itemPs.setObject(1, item.getId());
                        itemPs.setObject(2, order.getId());
                        itemPs.setString(3, item.getProductName());
                        itemPs.setString(4, item.getProductSku());
                        itemPs.setInt(5, item.getQuantity());
                        itemPs.setBigDecimal(6, item.getUnitPrice());
                        itemPs.setBigDecimal(7, item.getAmount());
                        itemPs.setString(8, item.getStatus().name());
                        itemPs.setArray(9, conn.createArrayOf("text", item.getTags().toArray()));
                        itemPs.setTimestamp(10, Timestamp.valueOf(item.getCreatedAt()));
                        itemPs.addBatch();
                    }
                }
                
                orderPs.executeBatch();
                itemPs.executeBatch();
                conn.commit();
            } catch (SQLException | JsonProcessingException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Batch insert failed", e);
        }
    }

    @Override
    public Order fetchOrder(UUID orderId) {
        String orderSql = "SELECT * FROM orders_normalized WHERE id = ?";
        String itemsSql = "SELECT * FROM items_normalized WHERE order_id = ? ORDER BY created_at";
        
        try (Connection conn = connection.getConnection()) {
            Order order = null;
            
            try (PreparedStatement orderPs = conn.prepareStatement(orderSql)) {
                orderPs.setObject(1, orderId);
                ResultSet rs = orderPs.executeQuery();
                
                if (rs.next()) {
                    order = new Order();
                    order.setId((UUID) rs.getObject("id"));
                    order.setCustomerName(rs.getString("customer_name"));
                    order.setCustomerEmail(rs.getString("customer_email"));
                    order.setAmount(rs.getBigDecimal("amount"));
                    order.setStatus(OrderStatus.valueOf(rs.getString("status")));
                    order.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    order.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                    
                    String metadataJson = rs.getString("metadata");
                    if (metadataJson != null) {
                        order.setMetadata(objectMapper.readValue(metadataJson, Map.class));
                    }
                }
            }
            
            if (order != null) {
                try (PreparedStatement itemsPs = conn.prepareStatement(itemsSql)) {
                    itemsPs.setObject(1, orderId);
                    ResultSet rs = itemsPs.executeQuery();
                    
                    List<Item> items = new ArrayList<>();
                    while (rs.next()) {
                        items.add(resultSetToItem(rs));
                    }
                    order.setItems(items);
                }
            }
            
            return order;
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Fetch order failed", e);
        }
    }

    @Override
    public Order fetchFiltered(UUID orderId, String filter) {
        String orderSql = "SELECT * FROM orders_normalized WHERE id = ?";
        String itemsSql = "SELECT * FROM items_normalized WHERE order_id = ? AND status = ? ORDER BY created_at";
        
        try (Connection conn = connection.getConnection()) {
            Order order = null;
            
            try (PreparedStatement orderPs = conn.prepareStatement(orderSql)) {
                orderPs.setObject(1, orderId);
                ResultSet rs = orderPs.executeQuery();
                
                if (rs.next()) {
                    order = new Order();
                    order.setId((UUID) rs.getObject("id"));
                    order.setCustomerName(rs.getString("customer_name"));
                    order.setCustomerEmail(rs.getString("customer_email"));
                    order.setAmount(rs.getBigDecimal("amount"));
                    order.setStatus(OrderStatus.valueOf(rs.getString("status")));
                    order.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    order.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                    
                    String metadataJson = rs.getString("metadata");
                    if (metadataJson != null) {
                        order.setMetadata(objectMapper.readValue(metadataJson, Map.class));
                    }
                }
            }
            
            if (order != null) {
                try (PreparedStatement itemsPs = conn.prepareStatement(itemsSql)) {
                    itemsPs.setObject(1, orderId);
                    itemsPs.setString(2, filter);
                    ResultSet rs = itemsPs.executeQuery();
                    
                    List<Item> items = new ArrayList<>();
                    while (rs.next()) {
                        items.add(resultSetToItem(rs));
                    }
                    order.setItems(items);
                }
            }
            
            return order;
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Fetch filtered failed", e);
        }
    }

    @Override
    public long count(UUID orderId) {
        String sql = "SELECT COUNT(*) FROM items_normalized WHERE order_id = ?";
        
        try (Connection conn = connection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setObject(1, orderId);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("Count failed", e);
        }
    }

    @Override
    public double aggregate(UUID orderId) {
        String sql = "SELECT amount FROM orders_normalized WHERE id = ?";
        
        try (Connection conn = connection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setObject(1, orderId);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                BigDecimal amount = rs.getBigDecimal("amount");
                return amount != null ? amount.doubleValue() : 0.0;
            }
            return 0.0;
        } catch (SQLException e) {
            throw new RuntimeException("Aggregate failed", e);
        }
    }

    @Override
    public List<Order> batchFetch(List<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        String placeholders = String.join(",", Collections.nCopies(orderIds.size(), "?"));
        String orderSql = "SELECT * FROM orders_normalized WHERE id IN (" + placeholders + ")";
        String itemsSql = "SELECT * FROM items_normalized WHERE order_id IN (" + placeholders + ") ORDER BY order_id, created_at";
        
        try (Connection conn = connection.getConnection()) {
            Map<UUID, Order> orderMap = new HashMap<>();
            
            try (PreparedStatement orderPs = conn.prepareStatement(orderSql)) {
                for (int i = 0; i < orderIds.size(); i++) {
                    orderPs.setObject(i + 1, orderIds.get(i));
                }
                ResultSet rs = orderPs.executeQuery();
                
                while (rs.next()) {
                    Order order = new Order();
                    UUID orderId = (UUID) rs.getObject("id");
                    order.setId(orderId);
                    order.setCustomerName(rs.getString("customer_name"));
                    order.setCustomerEmail(rs.getString("customer_email"));
                    order.setAmount(rs.getBigDecimal("amount"));
                    order.setStatus(OrderStatus.valueOf(rs.getString("status")));
                    order.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    order.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                    
                    String metadataJson = rs.getString("metadata");
                    if (metadataJson != null) {
                        order.setMetadata(objectMapper.readValue(metadataJson, Map.class));
                    }
                    
                    order.setItems(new ArrayList<>());
                    orderMap.put(orderId, order);
                }
            }
            
            try (PreparedStatement itemsPs = conn.prepareStatement(itemsSql)) {
                for (int i = 0; i < orderIds.size(); i++) {
                    itemsPs.setObject(i + 1, orderIds.get(i));
                }
                ResultSet rs = itemsPs.executeQuery();
                
                while (rs.next()) {
                    UUID orderId = (UUID) rs.getObject("order_id");
                    Order order = orderMap.get(orderId);
                    if (order != null) {
                        order.getItems().add(resultSetToItem(rs));
                    }
                }
            }
            
            return new ArrayList<>(orderMap.values());
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Batch fetch failed", e);
        }
    }

    private Item resultSetToItem(ResultSet rs) throws SQLException {
        Item item = new Item();
        item.setId((UUID) rs.getObject("id"));
        item.setProductName(rs.getString("product_name"));
        item.setProductSku(rs.getString("product_sku"));
        item.setQuantity(rs.getInt("quantity"));
        item.setUnitPrice(rs.getBigDecimal("unit_price"));
        item.setAmount(rs.getBigDecimal("amount"));
        item.setStatus(ItemStatus.valueOf(rs.getString("status")));
        
        Array tagsArray = rs.getArray("tags");
        if (tagsArray != null) {
            String[] tags = (String[]) tagsArray.getArray();
            item.setTags(Arrays.asList(tags));
        }
        
        item.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return item;
    }
}
