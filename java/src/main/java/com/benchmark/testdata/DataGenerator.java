package com.benchmark.testdata;

import com.benchmark.models.Item;
import com.benchmark.models.ItemStatus;
import com.benchmark.models.Order;
import com.benchmark.models.OrderStatus;
import com.github.javafaker.Faker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DataGenerator {
    private final Faker faker;
    private final Random random;

    public DataGenerator() {
        this.faker = new Faker();
        this.random = new Random();
    }

    public Order generateOrder(int minItems, int maxItems, double minPrice, double maxPrice, int minQty, int maxQty) {
        int itemCount = ThreadLocalRandom.current().nextInt(minItems, maxItems + 1);
        List<Item> items = new ArrayList<>();
        
        UUID orderId = UUID.randomUUID();
        for (int i = 0; i < itemCount; i++) {
            items.add(generateItem(orderId, minPrice, maxPrice, minQty, maxQty));
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("shipping_address", faker.address().fullAddress());
        metadata.put("payment_method", "credit_card");
        metadata.put("notes", faker.lorem().sentence());

        OrderStatus orderStatus = getRandomOrderStatus();
        String customerName = faker.name().fullName();
        String customerEmail = faker.internet().emailAddress();

        return new Order(orderId, customerName, customerEmail, orderStatus, metadata, items);
    }

    public Item generateItem(UUID orderId, double minPrice, double maxPrice, int minQty, int maxQty) {
        String productName = faker.commerce().productName();
        String productSku = "SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        int quantity = ThreadLocalRandom.current().nextInt(minQty, maxQty + 1);
        BigDecimal unitPrice = BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(minPrice, maxPrice))
                .setScale(2, RoundingMode.HALF_UP);
        ItemStatus status = getRandomItemStatus();
        List<String> tags = generateTags();

        return new Item(orderId, productName, productSku, quantity, unitPrice, status, tags);
    }

    public List<Item> generateItems(UUID orderId, int count, double minPrice, double maxPrice, int minQty, int maxQty) {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(generateItem(orderId, minPrice, maxPrice, minQty, maxQty));
        }
        return items;
    }

    private List<String> generateTags() {
        List<String> possibleTags = Arrays.asList("fragile", "perishable", "bulky", "express", "gift");
        int tagCount = random.nextInt(3);
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < tagCount; i++) {
            tags.add(possibleTags.get(random.nextInt(possibleTags.size())));
        }
        return tags;
    }

    private OrderStatus getRandomOrderStatus() {
        OrderStatus[] statuses = OrderStatus.values();
        return statuses[random.nextInt(statuses.length)];
    }

    private ItemStatus getRandomItemStatus() {
        ItemStatus[] statuses = ItemStatus.values();
        return statuses[random.nextInt(statuses.length)];
    }

    public UUID selectRandomOrderId(List<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            throw new IllegalArgumentException("Order IDs list is empty");
        }
        return orderIds.get(random.nextInt(orderIds.size()));
    }

    public List<UUID> selectRandomOrderIds(List<UUID> orderIds, int count) {
        if (orderIds.size() < count) {
            throw new IllegalArgumentException("Not enough order IDs");
        }
        Collections.shuffle(orderIds);
        return new ArrayList<>(orderIds.subList(0, count));
    }
}
