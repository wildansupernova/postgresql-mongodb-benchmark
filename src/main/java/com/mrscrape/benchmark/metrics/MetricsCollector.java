package com.mrscrape.benchmark.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MetricsCollector {
    private static class OperationMetrics {
        String operationName;
        long startTimeNs;
        long endTimeNs;
        List<Double> latenciesMs;

        OperationMetrics(String operationName) {
            this.operationName = operationName;
            this.startTimeNs = 0;
            this.endTimeNs = 0;
            this.latenciesMs = new CopyOnWriteArrayList<>();
        }
    }

    private final Map<String, OperationMetrics> metrics = new ConcurrentHashMap<>();
    private final List<String> failedOperations = new CopyOnWriteArrayList<>();

    public void startTime(String operationName) {
        metrics.computeIfAbsent(operationName, k -> new OperationMetrics(operationName));
        OperationMetrics om = metrics.get(operationName);
        om.startTimeNs = System.nanoTime();
    }

    public void endTime(String operationName) {
        OperationMetrics om = metrics.get(operationName);
        if (om != null) {
            om.endTimeNs = System.nanoTime();
        }
    }

    public void recordLatency(String operationName, double latencyMs) {
        OperationMetrics om = metrics.computeIfAbsent(operationName, k -> new OperationMetrics(operationName));
        om.latenciesMs.add(latencyMs);
    }

    public long getThroughput(String operationName) {
        OperationMetrics om = metrics.get(operationName);
        if (om == null || om.latenciesMs.isEmpty() || om.startTimeNs == 0 || om.endTimeNs == 0) {
            return 0;
        }
        double elapsedSeconds = (om.endTimeNs - om.startTimeNs) / 1_000_000_000.0;
        return elapsedSeconds > 0 ? (long) (om.latenciesMs.size() / elapsedSeconds) : 0;
    }

    public double getAverageDuration(String operationName) {
        OperationMetrics om = metrics.get(operationName);
        if (om == null || om.latenciesMs.isEmpty()) {
            return 0;
        }
        return om.latenciesMs.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    public double getPercentileDuration(String operationName, double percentile) {
        OperationMetrics om = metrics.get(operationName);
        if (om == null || om.latenciesMs.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(om.latenciesMs);
        Collections.sort(sorted);
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    public double getP50(String operationName) {
        return getPercentileDuration(operationName, 50);
    }

    public double getP75(String operationName) {
        return getPercentileDuration(operationName, 75);
    }

    public double getP99(String operationName) {
        return getPercentileDuration(operationName, 99);
    }

    public int getOperationCount(String operationName) {
        OperationMetrics om = metrics.get(operationName);
        return om != null ? om.latenciesMs.size() : 0;
    }

    public Set<String> getOperationNames() {
        return metrics.keySet();
    }

    public void reset() {
        metrics.clear();
    }

    public Map<String, Object> getMetricsForOperation(String operationName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operation", operationName);
        result.put("count", getOperationCount(operationName));
        result.put("throughput_ops_per_sec", getThroughput(operationName));
        result.put("avg_duration_ms", getAverageDuration(operationName));
        result.put("p50_duration_ms", getP50(operationName));
        result.put("p75_duration_ms", getP75(operationName));
        result.put("p99_duration_ms", getP99(operationName));
        return result;
    }

    public void recordFailure(String operationName, Exception e) {
        failedOperations.add(operationName + ": " + e.getMessage());
    }

    public int getFailureCount() {
        return failedOperations.size();
    }

    public List<String> getFailures() {
        return new ArrayList<>(failedOperations);
    }
}

