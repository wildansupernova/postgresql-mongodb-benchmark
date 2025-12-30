package com.mrscrape.benchmark.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Order {
    @JsonProperty("_id")
    private String orderId;

    @JsonProperty("customer_id")
    private String customerId;

    @JsonProperty("order_date")
    private Instant orderDate;

    @JsonProperty("total_amount")
    private long totalAmount;

    @JsonProperty("status")
    private String status;

    @JsonProperty("items")
    private List<Item> items;

    public Order() {
        this.items = new ArrayList<>();
        this.status = "pending";
    }

    public Order(String orderId, String customerId, Instant orderDate, String status) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.orderDate = orderDate;
        this.status = status;
        this.items = new ArrayList<>();
        this.totalAmount = 0;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public Instant getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(Instant orderDate) {
        this.orderDate = orderDate;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(long totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public void addItem(Item item) {
        item.setOrderId(this.orderId);
        this.items.add(item);
    }

    public long calculateTotalAmount() {
        return items.stream()
                .mapToLong(Item::calculateLineTotal)
                .sum();
    }

    public void recalculateTotalAmount() {
        this.totalAmount = calculateTotalAmount();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(orderId, order.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", orderDate=" + orderDate +
                ", totalAmount=" + totalAmount +
                ", status='" + status + '\'' +
                ", items=" + items.size() +
                '}';
    }
}
