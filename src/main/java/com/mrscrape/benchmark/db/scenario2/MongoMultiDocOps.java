package com.mrscrape.benchmark.db.scenario2;

import com.mrscrape.benchmark.db.DatabaseOperations;
import com.mrscrape.benchmark.db.MongoConnection;
import com.mrscrape.benchmark.db.RetryUtil;
import com.mrscrape.benchmark.model.Item;
import com.mrscrape.benchmark.model.Order;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MongoMultiDocOps implements DatabaseOperations {
    private final MongoConnection mongoConnection;
    private static final String ORDERS_COLLECTION = "orders";
    private static final String ITEMS_COLLECTION = "items";

    public MongoMultiDocOps(MongoConnection mongoConnection) {
        this.mongoConnection = mongoConnection;
    }

    @Override
    public void setup() throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            MongoCollection<Document> ordersCollection = mongoConnection.getDatabase().getCollection(ORDERS_COLLECTION);
            MongoCollection<Document> itemsCollection = mongoConnection.getDatabase().getCollection(ITEMS_COLLECTION);
            
            ordersCollection.drop();
            itemsCollection.drop();
            
            mongoConnection.getDatabase().createCollection(ORDERS_COLLECTION);
            mongoConnection.getDatabase().createCollection(ITEMS_COLLECTION);
            
            itemsCollection.createIndex(new Document("order_id", 1));
        }, "MongoMultiDocOps.setup");
    }

    @Override
    public void teardown() throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            MongoCollection<Document> ordersCollection = mongoConnection.getDatabase().getCollection(ORDERS_COLLECTION);
            MongoCollection<Document> itemsCollection = mongoConnection.getDatabase().getCollection(ITEMS_COLLECTION);
            
            ordersCollection.drop();
            itemsCollection.drop();
        }, "MongoMultiDocOps.teardown");
    }

    @Override
    public void insert(Order order) throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            MongoCollection<Document> ordersCollection = mongoConnection.getDatabase().getCollection(ORDERS_COLLECTION);
            MongoCollection<Document> itemsCollection = mongoConnection.getDatabase().getCollection(ITEMS_COLLECTION);
            
            try (ClientSession session = mongoConnection.getClient().startSession()) {
                session.startTransaction();
                
                try {
                    order.recalculateTotalAmount();
                    
                    Document orderDoc = new Document()
                            .append("_id", order.getOrderId())
                            .append("customer_id", order.getCustomerId())
                            .append("order_date", order.getOrderDate())
                            .append("total_amount", order.getTotalAmount())
                            .append("status", order.getStatus());
                    
                    ordersCollection.insertOne(session, orderDoc);
                    
                    for (Item item : order.getItems()) {
                        Document itemDoc = new Document()
                                .append("_id", item.getItemId())
                                .append("order_id", item.getOrderId())
                                .append("product_id", item.getProductId())
                                .append("name", item.getName())
                                .append("price", item.getPrice())
                                .append("quantity", item.getQuantity());
                        
                        itemsCollection.insertOne(session, itemDoc);
                    }
                    
                    session.commitTransaction();
                } catch (Exception e) {
                    if (isDuplicateKeyException(e)) {
                        session.commitTransaction();
                        return;
                    }
                    session.abortTransaction();
                    throw e;
                }
            }
        }, "MongoMultiDocOps.insert");
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
            MongoCollection<Document> ordersCollection = mongoConnection.getDatabase().getCollection(ORDERS_COLLECTION);
            MongoCollection<Document> itemsCollection = mongoConnection.getDatabase().getCollection(ITEMS_COLLECTION);
            
            try (ClientSession session = mongoConnection.getClient().startSession()) {
                session.startTransaction();
                
                try {
                    List<Document> items = itemsCollection.find(session, Filters.eq("order_id", orderId))
                            .into(new ArrayList<>());
                    
                    if (items.isEmpty()) {
                        throw new Exception("No items found for order: " + orderId);
                    }
                    
                    long newTotal = 0;
                    ThreadLocalRandom rand = ThreadLocalRandom.current();
                    for (Document item : items) {
                        long newPrice = item.getLong("price") + (rand.nextInt(100) + 1);
                        long newQuantity = Math.max(1, item.getLong("quantity") + (rand.nextInt(5) - 2));
                        
                        itemsCollection.updateOne(session,
                                Filters.eq("_id", item.getString("_id")),
                                new Document("$set", new Document()
                                        .append("price", newPrice)
                                        .append("quantity", newQuantity))
                        );
                        
                        newTotal += newPrice * newQuantity;
                    }
                    
                    ordersCollection.updateOne(session,
                            Filters.eq("_id", orderId),
                            new Document("$set", new Document("total_amount", newTotal))
                    );
                    
                    session.commitTransaction();
                } catch (Exception e) {
                    session.abortTransaction();
                    throw e;
                }
            }
        }, "MongoMultiDocOps.updateModify");
    }

    @Override
    public void updateAdd(String orderId) throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            MongoCollection<Document> ordersCollection = mongoConnection.getDatabase().getCollection(ORDERS_COLLECTION);
            MongoCollection<Document> itemsCollection = mongoConnection.getDatabase().getCollection(ITEMS_COLLECTION);
            
            try (ClientSession session = mongoConnection.getClient().startSession()) {
                session.startTransaction();
                
                try {
                    List<Document> items = itemsCollection.find(session, Filters.eq("order_id", orderId))
                            .into(new ArrayList<>());
                    
                    Document orderDoc = ordersCollection.find(session, Filters.eq("_id", orderId))
                            .first();
                    
                    if (orderDoc == null) {
                        throw new Exception("Order not found: " + orderId);
                    }
                    
                    int currentSize = items.size();
                    long newTotal = 0;
                    ThreadLocalRandom rand = ThreadLocalRandom.current();
                    
                    for (Document item : items) {
                        newTotal += item.getLong("price") * item.getLong("quantity");
                    }
                    
                    for (int i = 0; i < 5; i++) {
                        String itemId = orderId + "_item_" + (currentSize + i + 1);
                        long price = (long)(rand.nextDouble() * 900) + 100;
                        long quantity = rand.nextInt(10) + 1;
                        
                        Document newItem = new Document()
                                .append("_id", itemId)
                                .append("order_id", orderId)
                                .append("product_id", "product_" + (currentSize + i + 1))
                                .append("name", "New Product " + (currentSize + i + 1))
                                .append("price", price)
                                .append("quantity", quantity);
                        
                        itemsCollection.insertOne(session, newItem);
                        newTotal += price * quantity;
                    }
                    
                    ordersCollection.updateOne(session,
                            Filters.eq("_id", orderId),
                            new Document("$set", new Document("total_amount", newTotal))
                    );
                    
                    session.commitTransaction();
                } catch (Exception e) {
                    session.abortTransaction();
                    throw e;
                }
            }
        }, "MongoMultiDocOps.updateAdd");
    }

    @Override
    public Order query(String orderId) throws Exception {
        return RetryUtil.executeWithRetry(() -> {
            MongoCollection<Document> ordersCollection = mongoConnection.getDatabase().getCollection(ORDERS_COLLECTION);
            MongoCollection<Document> itemsCollection = mongoConnection.getDatabase().getCollection(ITEMS_COLLECTION);
            
            Document orderDoc = ordersCollection.find(Filters.eq("_id", orderId)).first();
            if (orderDoc == null) {
                throw new Exception("Order not found: " + orderId);
            }
            
            Order order = new Order(
                    orderDoc.getString("_id"),
                    orderDoc.getString("customer_id"),
                    orderDoc.getDate("order_date").toInstant(),
                    orderDoc.getString("status")
            );
            order.setTotalAmount(orderDoc.getLong("total_amount"));
            
            List<Document> itemDocs = itemsCollection.find(Filters.eq("order_id", orderId))
                    .into(new ArrayList<>());
            
            for (Document itemDoc : itemDocs) {
                Item item = new Item(
                        itemDoc.getString("_id"),
                        orderId,
                        itemDoc.getString("product_id"),
                        itemDoc.getString("name"),
                        itemDoc.getLong("price"),
                        itemDoc.getLong("quantity")
                );
                order.addItem(item);
            }
            
            validateTotalAmount(orderId);
            return order;
        }, "MongoMultiDocOps.query");
    }

    @Override
    public void delete(String orderId) throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            MongoCollection<Document> ordersCollection = mongoConnection.getDatabase().getCollection(ORDERS_COLLECTION);
            MongoCollection<Document> itemsCollection = mongoConnection.getDatabase().getCollection(ITEMS_COLLECTION);
            
            try (ClientSession session = mongoConnection.getClient().startSession()) {
                session.startTransaction();
                
                try {
                    itemsCollection.deleteMany(session, Filters.eq("order_id", orderId));
                    ordersCollection.deleteOne(session, Filters.eq("_id", orderId));
                    
                    session.commitTransaction();
                } catch (Exception e) {
                    session.abortTransaction();
                    throw e;
                }
            }
        }, "MongoMultiDocOps.delete");
    }

    @Override
    public void validateTotalAmount(String orderId) throws Exception {
        RetryUtil.executeVoidWithRetry(() -> {
            MongoCollection<Document> ordersCollection = mongoConnection.getDatabase().getCollection(ORDERS_COLLECTION);
            MongoCollection<Document> itemsCollection = mongoConnection.getDatabase().getCollection(ITEMS_COLLECTION);
            
            Document orderDoc = ordersCollection.find(Filters.eq("_id", orderId)).first();
            if (orderDoc == null) {
                throw new Exception("Order not found for validation: " + orderId);
            }
            
            List<Document> items = itemsCollection.find(Filters.eq("order_id", orderId))
                    .into(new ArrayList<>());
            
            long calculatedTotal = items.stream()
                    .mapToLong(item -> item.getLong("price") * item.getLong("quantity"))
                    .sum();
            
            long storedTotal = orderDoc.getLong("total_amount");
            if (calculatedTotal != storedTotal) {
                throw new Exception("Total amount mismatch for order " + orderId + 
                        ": calculated=" + calculatedTotal + ", stored=" + storedTotal);
            }
        }, "MongoMultiDocOps.validateTotalAmount");
    }
}
