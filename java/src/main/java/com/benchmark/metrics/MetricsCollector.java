package com.benchmark.metrics;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MetricsCollector {
    private static final int SHARD_COUNT = 100;
    private final List<Long>[] arrayOfLatencies;
    private long startTime;
    private long endTime;

    @SuppressWarnings("unchecked")
    public MetricsCollector() {
        this.arrayOfLatencies = new List[SHARD_COUNT];
        for (int i = 0; i < SHARD_COUNT; i++) {
            this.arrayOfLatencies[i] = Collections.synchronizedList(new ArrayList<>());
        }
    }

    public void startOperation() {
        this.startTime = System.currentTimeMillis();
    }

    public void recordLatency(long latencyMs) {
        int randomIndex = ThreadLocalRandom.current().nextInt(SHARD_COUNT);
        arrayOfLatencies[randomIndex].add(latencyMs);
    }

    public void endOperation(long latencyMs) {
        int randomIndex = ThreadLocalRandom.current().nextInt(SHARD_COUNT);
        arrayOfLatencies[randomIndex].add(latencyMs);
    }

    public MetricsResult getResults() {
        List<Long> combined = new ArrayList<>();
        for (List<Long> shard : arrayOfLatencies) {
            combined.addAll(shard);
        }
        
        if (combined.isEmpty()) {
            return new MetricsResult(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        List<Long> sorted = new ArrayList<>(combined);
        Collections.sort(sorted);

        double p50 = percentile(sorted, 50);
        double p95 = percentile(sorted, 95);
        double p99 = percentile(sorted, 99);
        double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0);
        long min = sorted.get(0);
        long max = sorted.get(sorted.size() - 1);

        long totalDurationMs = endTime - startTime;
        double throughput = totalDurationMs > 0 ? (combined.size() * 1000.0) / totalDurationMs : 0;

        return new MetricsResult(p50, p95, p99, avg, min, max, throughput, 0, 0);
    }

    public void start() {
        this.startTime = System.currentTimeMillis();
    }

    public void stop() {
        this.endTime = System.currentTimeMillis();
    }

    private double percentile(List<Long> sorted, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    public void reset() {
        for (List<Long> shard : arrayOfLatencies) {
            shard.clear();
        }
    }
}
