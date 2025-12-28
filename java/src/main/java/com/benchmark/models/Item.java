package com.benchmark.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class Item {
    private UUID id;
    private UUID orderId;
    private String productName;
    private String productSku;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private ItemStatus status;
    private List<String> tags;
    private LocalDateTime createdAt;

    public Item() {
        this.id = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
    }

    public Item(UUID orderId, String productName, String productSku, int quantity, BigDecimal unitPrice, ItemStatus status, List<String> tags) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.productName = productName;
        this.productSku = productSku;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.amount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        this.status = status;
        this.tags = tags;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductSku() {
        return productSku;
    }

    public void setProductSku(String productSku) {
        this.productSku = productSku;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        if (this.unitPrice != null) {
            this.amount = this.unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
        this.amount = unitPrice.multiply(BigDecimal.valueOf(this.quantity));
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public void setStatus(ItemStatus status) {
        this.status = status;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
