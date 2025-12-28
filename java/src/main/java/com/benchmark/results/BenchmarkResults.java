package com.benchmark.results;

import com.benchmark.metrics.MetricsResult;

import java.util.HashMap;
import java.util.Map;

public class BenchmarkResults {
    private final String scenario;
    private final String scale;
    private final int concurrency;
    private final Map<String, DatabaseResults> operationResults;

    public BenchmarkResults(String scenario, String scale, int concurrency) {
        this.scenario = scenario;
        this.scale = scale;
        this.concurrency = concurrency;
        this.operationResults = new HashMap<>();
    }

    public void addResult(String operation, String database, MetricsResult result) {
        operationResults.putIfAbsent(operation, new DatabaseResults());
        operationResults.get(operation).addResult(database, result);
    }

    public String getScenario() {
        return scenario;
    }

    public String getScale() {
        return scale;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public Map<String, DatabaseResults> getOperationResults() {
        return operationResults;
    }

    public static class DatabaseResults {
        private final Map<String, MetricsResult> results;

        public DatabaseResults() {
            this.results = new HashMap<>();
        }

        public void addResult(String database, MetricsResult result) {
            results.put(database, result);
        }

        public Map<String, MetricsResult> getResults() {
            return results;
        }
    }
}
