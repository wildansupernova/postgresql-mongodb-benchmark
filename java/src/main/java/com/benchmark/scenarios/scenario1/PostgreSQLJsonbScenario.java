package com.benchmark.scenarios.scenario1;

import com.benchmark.database.PostgreSQLConnection;
import com.benchmark.models.Item;
import com.benchmark.models.ItemStatus;
import com.benchmark.models.Order;
import com.benchmark.models.OrderStatus;
import com.benchmark.scenarios.BenchmarkScenario;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.postgresql.util.PGobject;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class PostgreSQLJsonbScenario implements BenchmarkScenario {
    private final PostgreSQLConnection connection;
    private final ObjectMapper objectMapper;

    public PostgreSQLJsonbScenario(PostgreSQLConnection connection) {
        this.connection = connection;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void setup() {
        try (Connection conn = connection.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("TRUNCATE TABLE orders_jsonb CASCADE");
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
                stmt.execute("TRUNCATE TABLE orders_jsonb CASCADE");
            }
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Cleanup failed", e);
        }
    }

    @Override
    public UUID insert(Order order) {
        String sql = "INSERT INTO orders_jsonb (id, customer_name, customer_email, amount, status, created_at, updated_at, metadata, items) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)";
        
        try (Connection conn = connection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setObject(1, order.getId());
            ps.setString(2, order.getCustomerName());
            ps.setString(3, order.getCustomerEmail());
            ps.setBigDecimal(4, order.getAmount());
            ps.setString(5, order.getStatus().name());
            ps.setTimestamp(6, Timestamp.valueOf(order.getCreatedAt()));
            ps.setTimestamp(7, Timestamp.valueOf(order.getUpdatedAt()));
            ps.setString(8, objectMapper.writeValueAsString(order.getMetadata()));
            ps.setString(9, objectMapper.writeValueAsString(order.getItems()));
            
            ps.executeUpdate();
            conn.commit();
            return order.getId();
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Insert failed", e);
        }
    }

    @Override
    public void append(UUID orderId, List<Item> items) {
        String sql = "UPDATE orders_jsonb SET items = items || ?::jsonb WHERE id = ?";
        
        try (Connection conn = connection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, objectMapper.writeValueAsString(items));
            ps.setObject(2, orderId);
            
            ps.executeUpdate();
            conn.commit();
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Append failed", e);
        }
    }

    @Override
    public void update(UUID orderId, UUID itemId, Item updatedItem) {
        String sql = "UPDATE orders_jsonb SET items = (" +
                     "  SELECT jsonb_agg(" +
                     "    CASE " +
                     "      WHEN (item->>'id')::text = ? " +
                     "      THEN ?::jsonb " +
                     "      ELSE item " +
                     "    END" +
                     "  ) " +
                     "  FROM jsonb_array_elements(items) item" +
                     ") WHERE id = ?";
        
        try (Connection conn = connection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, itemId.toString());
            ps.setString(2, objectMapper.writeValueAsString(updatedItem));
            ps.setObject(3, orderId);
            
            ps.executeUpdate();
            conn.commit();
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Update failed", e);
        }
    }

    @Override
    public void delete(UUID orderId, UUID itemId) {
        String sql = "UPDATE orders_jsonb SET items = (" +
                     "  SELECT jsonb_agg(item) " +
                     "  FROM jsonb_array_elements(items) item " +
                     "  WHERE (item->>'id')::text != ?" +
                     ") WHERE id = ?";
        
        try (Connection conn = connection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, itemId.toString());
            ps.setObject(2, orderId);
            
            ps.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Delete failed", e);
        }
    }

    @Override
    public void batchInsert(List<Order> orders) {
        String sql = "INSERT INTO orders_jsonb (id, customer_name, customer_email, amount, status, created_at, updated_at, metadata, items) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)";
        
        try (Connection conn = connection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            for (Order order : orders) {
                ps.setObject(1, order.getId());
                ps.setString(2, order.getCustomerName());
                ps.setString(3, order.getCustomerEmail());
                ps.setBigDecimal(4, order.getAmount());
                ps.setString(5, order.getStatus().name());
                ps.setTimestamp(6, Timestamp.valueOf(order.getCreatedAt()));
                ps.setTimestamp(7, Timestamp.valueOf(order.getUpdatedAt()));
                ps.setString(8, objectMapper.writeValueAsString(order.getMetadata()));
                ps.setString(9, objectMapper.writeValueAsString(order.getItems()));
                ps.addBatch();
            }
            
            ps.executeBatch();
            conn.commit();
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Batch insert failed", e);
        }
    }

    @Override
    public Order fetchOrder(UUID orderId) {
        String sql = "SELECT * FROM orders_jsonb WHERE id = ?";
        
        try (Connection conn = connection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setObject(1, orderId);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                return resultSetToOrder(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Fetch order failed", e);
        }
    }

    @Override
    public Order fetchFiltered(UUID orderId, String filter) {
        String sql = "SELECT *, (" +
                     "  SELECT jsonb_agg(item) " +
                     "  FROM jsonb_array_elements(items) item " +
                     "  WHERE item->>'status' = ?" +
                     ") as filtered_items " +
                     "FROM orders_jsonb WHERE id = ?";
        
        try (Connection conn = connection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, filter);
            ps.setObject(2, orderId);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                Order order = resultSetToOrder(rs);
                String filteredItemsJson = rs.getString("filtered_items");
                if (filteredItemsJson != null) {
                    List<Item> filteredItems = objectMapper.readValue(filteredItemsJson, new TypeReference<List<Item>>() {});
                    order.setItems(filteredItems);
                }
                return order;
            }
            return null;
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Fetch filtered failed", e);
        }
    }

    @Override
    public long count(UUID orderId) {
        String sql = "SELECT jsonb_array_length(items) as count FROM orders_jsonb WHERE id = ?";
        
        try (Connection conn = connection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setObject(1, orderId);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                return rs.getLong("count");
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("Count failed", e);
        }
    }

    @Override
    public double aggregate(UUID orderId) {
        String sql = "SELECT amount FROM orders_jsonb WHERE id = ?";
        
        try (Connection conn = connection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setObject(1, orderId);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                return rs.getBigDecimal("amount").doubleValue();
            }
            return 0.0;
        } catch (SQLException e) {
            throw new RuntimeException("Aggregate failed", e);
        }
    }

    @Override
    public List<Order> batchFetch(List<UUID> orderIds) {
        String sql = "SELECT * FROM orders_jsonb WHERE id = ANY(?)";
        
        try (Connection conn = connection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            Array array = conn.createArrayOf("uuid", orderIds.toArray());
            ps.setArray(1, array);
            ResultSet rs = ps.executeQuery();
            
            List<Order> orders = new ArrayList<>();
            while (rs.next()) {
                orders.add(resultSetToOrder(rs));
            }
            return orders;
        } catch (SQLException e) {
            throw new RuntimeException("Batch fetch failed", e);
        }
    }

    private Order resultSetToOrder(ResultSet rs) throws SQLException {
        Order order = new Order();
        order.setId((UUID) rs.getObject("id"));
        order.setCustomerName(rs.getString("customer_name"));
        order.setCustomerEmail(rs.getString("customer_email"));
        order.setAmount(rs.getBigDecimal("amount"));
        order.setStatus(OrderStatus.valueOf(rs.getString("status")));
        order.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        order.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        
        try {
            String metadataJson = rs.getString("metadata");
            Map<String, Object> metadata = objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
            order.setMetadata(metadata);
            
            String itemsJson = rs.getString("items");
            List<Item> items = objectMapper.readValue(itemsJson, new TypeReference<List<Item>>() {});
            order.setItems(items);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to parse JSON", e);
        }
        
        return order;
    }
}
