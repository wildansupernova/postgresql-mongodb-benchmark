package com.mrscrape.benchmark.db.scenario1;

import com.mrscrape.benchmark.db.DatabaseOperations;
import com.mrscrape.benchmark.db.MongoConnection;
import com.mrscrape.benchmark.db.RetryUtil;
import com.mrscrape.benchmark.model.Item;
import com.mrscrape.benchmark.model.Order;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MongoEmbeddedOps implements DatabaseOperations {
    private final MongoConnection mongoConnection;
    private static final String COLLECTION_NAME = "orders";

    public MongoEmbeddedOps(MongoConnection mongoConnection) {
        this.mongoConnection = mongoConnection;
    }

    @Override
    public void setup() throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            MongoCollection<Document> collection = mongoConnection.getDatabase().getCollection(COLLECTION_NAME);
            collection.drop();
            mongoConnection.getDatabase().createCollection(COLLECTION_NAME);
        }, "MongoEmbeddedOps.setup");
    }

    @Override
    public void teardown() throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            MongoCollection<Document> collection = mongoConnection.getDatabase().getCollection(COLLECTION_NAME);
            collection.drop();
        }, "MongoEmbeddedOps.teardown");
    }

    @Override
    public void insert(Order order) throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            MongoCollection<Document> collection = mongoConnection.getDatabase().getCollection(COLLECTION_NAME)
                    .withWriteConcern(WriteConcern.JOURNALED);
            
            try {
                List<Document> itemDocs = new ArrayList<>();
                for (Item item : order.getItems()) {
                    Document itemDoc = new Document()
                            .append("item_id", item.getItemId())
                            .append("product_id", item.getProductId())
                            .append("name", item.getName())
                            .append("price", item.getPrice())
                            .append("quantity", item.getQuantity());
                    itemDocs.add(itemDoc);
                }
                
                order.recalculateTotalAmount();
                
                Document orderDoc = new Document()
                        .append("_id", order.getOrderId())
                        .append("customer_id", order.getCustomerId())
                        .append("order_date", order.getOrderDate())
                        .append("total_amount", order.getTotalAmount())
                        .append("status", order.getStatus())
                        .append("items", itemDocs);
                
                collection.insertOne(orderDoc);
            } catch (Exception e) {
                if (isDuplicateKeyException(e)) {
                    return;
                }
                throw e;
            }
        }, "MongoEmbeddedOps.insert");
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
        return message.contains("duplicate") || message.contains("already exists") || 
               (message.contains("e11000") && message.contains("duplicate"));
    }

    @Override
    public void updateModify(String orderId) throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            MongoCollection<Document> collection = mongoConnection.getDatabase().getCollection(COLLECTION_NAME)
                    .withWriteConcern(WriteConcern.JOURNALED);
            
            Document orderDoc = collection.find(new Document("_id", orderId)).first();
            if (orderDoc == null) {
                throw new Exception("Order not found: " + orderId);
            }
            
            List<Document> items = (List<Document>) orderDoc.get("items");
            if (items != null && !items.isEmpty()) {
                ThreadLocalRandom rand = ThreadLocalRandom.current();
                for (Document item : items) {
                    long newPrice = ((Number) item.get("price")).longValue() + (rand.nextInt(100) + 1);
                    long newQuantity = Math.max(1, ((Number) item.get("quantity")).longValue() + (rand.nextInt(5) - 2));
                    item.put("price", newPrice);
                    item.put("quantity", newQuantity);
                }
            }
            
            long newTotal = items.stream()
                    .mapToLong(item -> ((Number) item.get("price")).longValue() * ((Number) item.get("quantity")).longValue())
                    .sum();
            
            collection.updateOne(
                    new Document("_id", orderId),
                    new Document("$set", new Document()
                            .append("items", items)
                            .append("total_amount", newTotal))
            );
        }, "MongoEmbeddedOps.updateModify");
    }

    @Override
    public void updateAdd(String orderId) throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            MongoCollection<Document> collection = mongoConnection.getDatabase().getCollection(COLLECTION_NAME)
                    .withWriteConcern(WriteConcern.JOURNALED);
            
            Document orderDoc = collection.find(new Document("_id", orderId)).first();
            if (orderDoc == null) {
                throw new Exception("Order not found: " + orderId);
            }
            
            List<Document> items = (List<Document>) orderDoc.get("items");
            if (items == null) {
                items = new ArrayList<>();
            }
            
            int currentSize = items.size();
            ThreadLocalRandom rand = ThreadLocalRandom.current();
            for (int i = 0; i < 5; i++) {
                Document newItem = new Document()
                        .append("item_id", orderId + "_item_" + (currentSize + i + 1))
                        .append("product_id", "product_" + (currentSize + i + 1))
                        .append("name", "New Product " + (currentSize + i + 1))
                        .append("price", (long)(rand.nextDouble() * 900) + 100)
                        .append("quantity", rand.nextInt(10) + 1);
                items.add(newItem);
            }
            
            long newTotal = items.stream()
                    .mapToLong(item -> ((Number) item.get("price")).longValue() * ((Number) item.get("quantity")).longValue())
                    .sum();
            
            collection.updateOne(
                    new Document("_id", orderId),
                    new Document("$set", new Document()
                            .append("items", items)
                            .append("total_amount", newTotal))
            );
        }, "MongoEmbeddedOps.updateAdd");
    }

    @Override
    public Order query(String orderId) throws Exception {
        return RetryUtil.executeWithRetry(() -> {
            MongoCollection<Document> collection = mongoConnection.getDatabase().getCollection(COLLECTION_NAME);
            
            Document orderDoc = collection.find(new Document("_id", orderId)).first();
            if (orderDoc == null) {
                throw new Exception("Order not found: " + orderId);
            }
            
            Order order = new Order(
                    orderDoc.getString("_id"),
                    orderDoc.getString("customer_id"),
                    orderDoc.getDate("order_date").toInstant(),
                    orderDoc.getString("status")
            );
            order.setTotalAmount(((Number) orderDoc.get("total_amount")).longValue());
            
            List<Document> itemDocs = (List<Document>) orderDoc.get("items");
            if (itemDocs != null) {
                for (Document itemDoc : itemDocs) {
                    Item item = new Item(
                            itemDoc.getString("item_id"),
                            orderId,
                            itemDoc.getString("product_id"),
                            itemDoc.getString("name"),
                            ((Number) itemDoc.get("price")).longValue(),
                            ((Number) itemDoc.get("quantity")).longValue()
                    );
                    order.addItem(item);
                }
            }
            
            validateTotalAmount(orderId);
            return order;
        }, "MongoEmbeddedOps.query");
    }

    @Override
    public void delete(String orderId) throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            MongoCollection<Document> collection = mongoConnection.getDatabase().getCollection(COLLECTION_NAME)
                    .withWriteConcern(WriteConcern.JOURNALED);
            collection.deleteOne(new Document("_id", orderId));
        }, "MongoEmbeddedOps.delete");
    }

    @Override
    public void validateTotalAmount(String orderId) throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            MongoCollection<Document> collection = mongoConnection.getDatabase().getCollection(COLLECTION_NAME);
            
            Document orderDoc = collection.find(new Document("_id", orderId)).first();
            if (orderDoc == null) {
                throw new Exception("Order not found for validation: " + orderId);
            }
            
            List<Document> items = (List<Document>) orderDoc.get("items");
            long calculatedTotal = items.stream()
                    .mapToLong(item -> ((Number) item.get("price")).longValue() * ((Number) item.get("quantity")).longValue())
                    .sum();
            
            long storedTotal = ((Number) orderDoc.get("total_amount")).longValue();
            if (calculatedTotal != storedTotal) {
                throw new Exception("Total amount mismatch for order " + orderId + 
                        ": calculated=" + calculatedTotal + ", stored=" + storedTotal);
            }
        }, "MongoEmbeddedOps.validateTotalAmount");
    }
}
