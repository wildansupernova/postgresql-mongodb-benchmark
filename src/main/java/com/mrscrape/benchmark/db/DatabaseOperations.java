package com.mrscrape.benchmark.db;

import com.mrscrape.benchmark.model.Order;

public interface DatabaseOperations {
    void setup() throws Exception;
    
    void teardown() throws Exception;
    
    void insert(Order order) throws Exception;
    
    void updateModify(String orderId) throws Exception;
    
    void updateAdd(String orderId) throws Exception;
    
    Order query(String orderId) throws Exception;
    
    void delete(String orderId) throws Exception;
    
    void validateTotalAmount(String orderId) throws Exception;
}
