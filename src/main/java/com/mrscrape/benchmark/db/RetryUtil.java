package com.mrscrape.benchmark.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryUtil {
    private static final Logger logger = LoggerFactory.getLogger(RetryUtil.class);
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_INITIAL_BACKOFF_MS = 100;

    public interface RetryableOperation<T> {
        T execute() throws Exception;
    }

    public interface RetryableVoidOperation {
        void execute() throws Exception;
    }

    public static <T> T executeWithRetry(RetryableOperation<T> operation, String operationName) throws Exception {
        return executeWithRetry(operation, operationName, DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_BACKOFF_MS);
    }

    public static <T> T executeWithRetry(RetryableOperation<T> operation, String operationName, 
            int maxRetries, long initialBackoffMs) throws Exception {
        Exception lastException = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return operation.execute();
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetries - 1) {
                    long backoffMs = initialBackoffMs * (long) Math.pow(2, attempt);
                    long jitterMs = (long) (Math.random() * backoffMs);
                    long totalWaitMs = backoffMs + jitterMs;
                    logger.warn("{} failed, retrying in {}ms (attempt {}/{})", 
                        operationName, totalWaitMs, attempt + 1, maxRetries, e);
                    Thread.sleep(totalWaitMs);
                } else {
                    logger.error("{} failed after {} retries: {}", operationName, maxRetries, e.getMessage(), e);
                }
            }
        }

        throw new Exception("Operation failed after " + maxRetries + " retries: " + operationName, lastException);
    }

    public static void executeVoidWithRetry(RetryableVoidOperation operation, String operationName) throws Exception {
        executeVoidWithRetry(operation, operationName, DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_BACKOFF_MS);
    }

    public static void executeVoidWithRetry(RetryableVoidOperation operation, String operationName, 
            int maxRetries, long initialBackoffMs) throws Exception {
        Exception lastException = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                operation.execute();
                return;
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetries - 1) {
                    long backoffMs = initialBackoffMs * (long) Math.pow(2, attempt);
                    long jitterMs = (long) (Math.random() * backoffMs);
                    long totalWaitMs = backoffMs + jitterMs;
                    logger.warn("{} failed, retrying in {}ms (attempt {}/{})", 
                        operationName, totalWaitMs, attempt + 1, maxRetries, e);
                    Thread.sleep(totalWaitMs);
                } else {
                    logger.error("{} failed after {} retries: {}", operationName, maxRetries, e.getMessage(), e);
                }
            }
        }

        throw new Exception("Operation failed after " + maxRetries + " retries: " + operationName, lastException);
    }
}
