package com.benchmark.scenarios.scenario1;

import com.benchmark.database.MongoDBConnection;
import com.benchmark.models.Item;
import com.benchmark.models.ItemStatus;
import com.benchmark.models.Order;
import com.benchmark.models.OrderStatus;
import com.benchmark.scenarios.BenchmarkScenario;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.Decimal128;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class MongoDBEmbeddedScenario implements BenchmarkScenario {
    private final MongoDBConnection connection;
    private MongoCollection<Document> collection;

    public MongoDBEmbeddedScenario(MongoDBConnection connection) {
        this.connection = connection;
    }

    @Override
    public void setup() {
        MongoDatabase db = connection.getDatabase();
        collection = db.getCollection("orders_embedded");
        collection.drop();
    }

    @Override
    public void cleanup() {
        if (collection != null) {
            collection.drop();
        }
    }

    @Override
    public UUID insert(Order order) {
        Document doc = orderToDocument(order);
        collection.insertOne(doc);
        return order.getId();
    }

    @Override
    public void append(UUID orderId, List<Item> items) {
        List<Document> itemDocs = items.stream()
                .map(this::itemToDocument)
                .collect(Collectors.toList());
        
        collection.updateOne(
                Filters.eq("_id", orderId.toString()),
                Updates.pushEach("items", itemDocs)
        );
    }

    @Override
    public void update(UUID orderId, UUID itemId, Item updatedItem) {
        collection.updateOne(
                Filters.and(
                        Filters.eq("_id", orderId.toString()),
                        Filters.eq("items._id", itemId.toString())
                ),
                Updates.combine(
                        Updates.set("items.$.product_name", updatedItem.getProductName()),
                        Updates.set("items.$.quantity", updatedItem.getQuantity()),
                        Updates.set("items.$.unit_price", new Decimal128(updatedItem.getUnitPrice())),
                        Updates.set("items.$.amount", new Decimal128(updatedItem.getAmount())),
                        Updates.set("items.$.status", updatedItem.getStatus().name())
                )
        );
    }

    @Override
    public void delete(UUID orderId, UUID itemId) {
        collection.updateOne(
                Filters.eq("_id", orderId.toString()),
                Updates.pull("items", new Document("_id", itemId.toString()))
        );
    }

    @Override
    public void batchInsert(List<Order> orders) {
        List<Document> docs = orders.stream()
                .map(this::orderToDocument)
                .collect(Collectors.toList());
        collection.insertMany(docs);
    }

    @Override
    public Order fetchOrder(UUID orderId) {
        Document doc = collection.find(Filters.eq("_id", orderId.toString())).first();
        return doc != null ? documentToOrder(doc) : null;
    }

    @Override
    public Order fetchFiltered(UUID orderId, String filter) {
        Document doc = collection.find(
                Filters.and(
                        Filters.eq("_id", orderId.toString()),
                        Filters.eq("items.status", filter)
                )
        ).first();
        return doc != null ? documentToOrder(doc) : null;
    }

    @Override
    public long count(UUID orderId) {
        Document doc = collection.find(Filters.eq("_id", orderId.toString())).first();
        if (doc != null && doc.containsKey("items")) {
            List<?> items = doc.getList("items", Document.class);
            return items != null ? items.size() : 0;
        }
        return 0;
    }

    @Override
    public double aggregate(UUID orderId) {
        Document doc = collection.find(Filters.eq("_id", orderId.toString())).first();
        if (doc != null && doc.containsKey("amount")) {
            Object amount = doc.get("amount");
            if (amount instanceof Decimal128) {
                return ((Decimal128) amount).bigDecimalValue().doubleValue();
            }
        }
        return 0.0;
    }

    @Override
    public List<Order> batchFetch(List<UUID> orderIds) {
        List<String> ids = orderIds.stream().map(UUID::toString).collect(Collectors.toList());
        return collection.find(Filters.in("_id", ids))
                .into(new ArrayList<>())
                .stream()
                .map(this::documentToOrder)
                .collect(Collectors.toList());
    }

    private Document orderToDocument(Order order) {
        Document doc = new Document();
        doc.put("_id", order.getId().toString());
        doc.put("customer_name", order.getCustomerName());
        doc.put("customer_email", order.getCustomerEmail());
        doc.put("amount", new Decimal128(order.getAmount()));
        doc.put("status", order.getStatus().name());
        doc.put("created_at", Date.from(order.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()));
        doc.put("updated_at", Date.from(order.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant()));
        doc.put("metadata", new Document(order.getMetadata()));
        
        List<Document> itemDocs = order.getItems().stream()
                .map(this::itemToDocument)
                .collect(Collectors.toList());
        doc.put("items", itemDocs);
        
        return doc;
    }

    private Document itemToDocument(Item item) {
        Document doc = new Document();
        doc.put("_id", item.getId().toString());
        doc.put("product_name", item.getProductName());
        doc.put("product_sku", item.getProductSku());
        doc.put("quantity", item.getQuantity());
        doc.put("unit_price", new Decimal128(item.getUnitPrice()));
        doc.put("amount", new Decimal128(item.getAmount()));
        doc.put("status", item.getStatus().name());
        doc.put("tags", item.getTags());
        doc.put("created_at", Date.from(item.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()));
        return doc;
    }

    private Order documentToOrder(Document doc) {
        Order order = new Order();
        order.setId(UUID.fromString(doc.getString("_id")));
        order.setCustomerName(doc.getString("customer_name"));
        order.setCustomerEmail(doc.getString("customer_email"));
        
        Object amountObj = doc.get("amount");
        if (amountObj instanceof Decimal128) {
            order.setAmount(((Decimal128) amountObj).bigDecimalValue());
        }
        
        order.setStatus(OrderStatus.valueOf(doc.getString("status")));
        
        Date createdAtDate = doc.getDate("created_at");
        order.setCreatedAt(LocalDateTime.ofInstant(createdAtDate.toInstant(), ZoneId.systemDefault()));
        
        Date updatedAtDate = doc.getDate("updated_at");
        order.setUpdatedAt(LocalDateTime.ofInstant(updatedAtDate.toInstant(), ZoneId.systemDefault()));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) doc.get("metadata", Document.class);
        order.setMetadata(metadata);
        
        @SuppressWarnings("unchecked")
        List<Document> itemDocs = (List<Document>) doc.get("items");
        if (itemDocs != null) {
            List<Item> items = itemDocs.stream()
                    .map(this::documentToItem)
                    .collect(Collectors.toList());
            order.setItems(items);
        }
        
        return order;
    }

    private Item documentToItem(Document doc) {
        Item item = new Item();
        item.setId(UUID.fromString(doc.getString("_id")));
        item.setProductName(doc.getString("product_name"));
        item.setProductSku(doc.getString("product_sku"));
        item.setQuantity(doc.getInteger("quantity"));
        
        Object unitPriceObj = doc.get("unit_price");
        if (unitPriceObj instanceof Decimal128) {
            item.setUnitPrice(((Decimal128) unitPriceObj).bigDecimalValue());
        }
        
        Object amountObj = doc.get("amount");
        if (amountObj instanceof Decimal128) {
            item.setAmount(((Decimal128) amountObj).bigDecimalValue());
        }
        
        item.setStatus(ItemStatus.valueOf(doc.getString("status")));
        
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) doc.get("tags");
        item.setTags(tags);
        
        Date createdAtDate = doc.getDate("created_at");
        item.setCreatedAt(LocalDateTime.ofInstant(createdAtDate.toInstant(), ZoneId.systemDefault()));
        
        return item;
    }
}
