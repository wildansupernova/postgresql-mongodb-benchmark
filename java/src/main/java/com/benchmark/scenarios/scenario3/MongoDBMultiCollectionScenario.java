package com.benchmark.scenarios.scenario3;

import com.benchmark.database.MongoDBConnection;
import com.benchmark.models.Item;
import com.benchmark.models.ItemStatus;
import com.benchmark.models.Order;
import com.benchmark.models.OrderStatus;
import com.benchmark.scenarios.BenchmarkScenario;
import com.mongodb.client.ClientSession;
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

public class MongoDBMultiCollectionScenario implements BenchmarkScenario {
    private final MongoDBConnection connection;
    private MongoCollection<Document> ordersCollection;
    private MongoCollection<Document> itemsCollection;

    public MongoDBMultiCollectionScenario(MongoDBConnection connection) {
        this.connection = connection;
    }

    @Override
    public void setup() {
        MongoDatabase db = connection.getDatabase();
        ordersCollection = db.getCollection("orders_normalized");
        itemsCollection = db.getCollection("items_normalized");
        ordersCollection.drop();
        itemsCollection.drop();
    }

    @Override
    public void cleanup() {
        if (ordersCollection != null) {
            ordersCollection.drop();
        }
        if (itemsCollection != null) {
            itemsCollection.drop();
        }
    }

    @Override
    public UUID insert(Order order) {
        try (ClientSession session = connection.getClient().startSession()) {
            session.startTransaction();
            try {
                Document orderDoc = orderToDocument(order);
                ordersCollection.insertOne(session, orderDoc);

                List<Document> itemDocs = order.getItems().stream()
                        .map(item -> itemToDocument(item, order.getId()))
                        .collect(Collectors.toList());

                if (!itemDocs.isEmpty()) {
                    itemsCollection.insertMany(session, itemDocs);
                }

                session.commitTransaction();
                return order.getId();
            } catch (Exception e) {
                session.abortTransaction();
                throw e;
            }
        }
    }

    @Override
    public void append(UUID orderId, List<Item> items) {
        try (ClientSession session = connection.getClient().startSession()) {
            session.startTransaction();
            try {
                List<Document> itemDocs = items.stream()
                        .map(item -> itemToDocument(item, orderId))
                        .collect(Collectors.toList());

                if (!itemDocs.isEmpty()) {
                    itemsCollection.insertMany(session, itemDocs);
                }

                updateOrderAmount(session, orderId);
                session.commitTransaction();
            } catch (Exception e) {
                session.abortTransaction();
                throw e;
            }
        }
    }

    @Override
    public void update(UUID orderId, UUID itemId, Item updatedItem) {
        try (ClientSession session = connection.getClient().startSession()) {
            session.startTransaction();
            try {
                itemsCollection.updateOne(
                        session,
                        Filters.eq("_id", itemId.toString()),
                        Updates.combine(
                                Updates.set("product_name", updatedItem.getProductName()),
                                Updates.set("quantity", updatedItem.getQuantity()),
                                Updates.set("unit_price", new Decimal128(updatedItem.getUnitPrice())),
                                Updates.set("amount", new Decimal128(updatedItem.getAmount())),
                                Updates.set("status", updatedItem.getStatus().name())
                        )
                );

                updateOrderAmount(session, orderId);
                session.commitTransaction();
            } catch (Exception e) {
                session.abortTransaction();
                throw e;
            }
        }
    }

    @Override
    public void delete(UUID orderId, UUID itemId) {
        try (ClientSession session = connection.getClient().startSession()) {
            session.startTransaction();
            try {
                itemsCollection.deleteOne(
                        session,
                        Filters.eq("_id", itemId.toString())
                );

                updateOrderAmount(session, orderId);
                session.commitTransaction();
            } catch (Exception e) {
                session.abortTransaction();
                throw e;
            }
        }
    }

    @Override
    public void batchInsert(List<Order> orders) {
        try (ClientSession session = connection.getClient().startSession()) {
            session.startTransaction();
            try {
                List<Document> orderDocs = orders.stream()
                        .map(this::orderToDocument)
                        .collect(Collectors.toList());
                ordersCollection.insertMany(session, orderDocs);

                List<Document> allItemDocs = new ArrayList<>();
                for (Order order : orders) {
                    order.getItems().stream()
                            .map(item -> itemToDocument(item, order.getId()))
                            .forEach(allItemDocs::add);
                }

                if (!allItemDocs.isEmpty()) {
                    itemsCollection.insertMany(session, allItemDocs);
                }

                session.commitTransaction();
            } catch (Exception e) {
                session.abortTransaction();
                throw e;
            }
        }
    }

    @Override
    public Order fetchOrder(UUID orderId) {
        Document orderDoc = ordersCollection.find(Filters.eq("_id", orderId.toString())).first();
        if (orderDoc == null) {
            return null;
        }

        Order order = documentToOrder(orderDoc);
        List<Document> itemDocs = itemsCollection.find(Filters.eq("order_id", orderId.toString()))
                .into(new ArrayList<>());

        List<Item> items = itemDocs.stream()
                .map(this::documentToItem)
                .collect(Collectors.toList());
        order.setItems(items);

        return order;
    }

    @Override
    public Order fetchFiltered(UUID orderId, String filter) {
        Document orderDoc = ordersCollection.find(Filters.eq("_id", orderId.toString())).first();
        if (orderDoc == null) {
            return null;
        }

        Order order = documentToOrder(orderDoc);
        List<Document> itemDocs = itemsCollection.find(
                Filters.and(
                        Filters.eq("order_id", orderId.toString()),
                        Filters.eq("status", filter)
                )
        ).into(new ArrayList<>());

        List<Item> items = itemDocs.stream()
                .map(this::documentToItem)
                .collect(Collectors.toList());
        order.setItems(items);

        return order;
    }

    @Override
    public long count(UUID orderId) {
        return itemsCollection.countDocuments(Filters.eq("order_id", orderId.toString()));
    }

    @Override
    public double aggregate(UUID orderId) {
        Document orderDoc = ordersCollection.find(Filters.eq("_id", orderId.toString())).first();
        if (orderDoc != null && orderDoc.containsKey("amount")) {
            Object amount = orderDoc.get("amount");
            if (amount instanceof Decimal128) {
                return ((Decimal128) amount).bigDecimalValue().doubleValue();
            }
        }
        return 0.0;
    }

    @Override
    public List<Order> batchFetch(List<UUID> orderIds) {
        List<String> ids = orderIds.stream().map(UUID::toString).collect(Collectors.toList());
        List<Document> orderDocs = ordersCollection.find(Filters.in("_id", ids))
                .into(new ArrayList<>());

        return orderDocs.stream().map(orderDoc -> {
            Order order = documentToOrder(orderDoc);
            UUID orderId = order.getId();

            List<Document> itemDocs = itemsCollection.find(Filters.eq("order_id", orderId.toString()))
                    .into(new ArrayList<>());

            List<Item> items = itemDocs.stream()
                    .map(this::documentToItem)
                    .collect(Collectors.toList());
            order.setItems(items);

            return order;
        }).collect(Collectors.toList());
    }

    private void updateOrderAmount(ClientSession session, UUID orderId) {
        List<Document> itemDocs = itemsCollection.find(session, Filters.eq("order_id", orderId.toString()))
                .into(new ArrayList<>());

        BigDecimal totalAmount = itemDocs.stream()
                .map(doc -> {
                    Object amount = doc.get("amount");
                    if (amount instanceof Decimal128) {
                        return ((Decimal128) amount).bigDecimalValue();
                    }
                    return BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ordersCollection.updateOne(
                session,
                Filters.eq("_id", orderId.toString()),
                Updates.combine(
                        Updates.set("amount", new Decimal128(totalAmount)),
                        Updates.set("updated_at", new Date())
                )
        );
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
        return doc;
    }

    private Document itemToDocument(Item item, UUID orderId) {
        Document doc = new Document();
        doc.put("_id", item.getId().toString());
        doc.put("order_id", orderId.toString());
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
        order.setItems(new ArrayList<>());

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
