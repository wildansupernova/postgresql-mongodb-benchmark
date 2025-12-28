package com.benchmark.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class BenchmarkConfig {
    private Map<String, Object> config;

    public BenchmarkConfig() {
        load();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("config.yaml")) {
            this.config = mapper.readValue(inputStream, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config.yaml", e);
        }
    }

    @SuppressWarnings("unchecked")
    public String getMongoUri() {
        Map<String, Object> scenarios = (Map<String, Object>) config.get("scenarios");
        Map<String, Object> scenario1 = (Map<String, Object>) scenarios.get("scenario1");
        return (String) scenario1.get("mongodb_uri");
    }

    @SuppressWarnings("unchecked")
    public String getPostgresHost() {
        Map<String, Object> scenarios = (Map<String, Object>) config.get("scenarios");
        Map<String, Object> scenario1 = (Map<String, Object>) scenarios.get("scenario1");
        return (String) scenario1.get("postgres_host");
    }

    @SuppressWarnings("unchecked")
    public String getMongoDatabase() {
        Map<String, Object> mongodb = (Map<String, Object>) config.get("mongodb");
        return (String) mongodb.get("database");
    }

    @SuppressWarnings("unchecked")
    public String getPostgresDatabase() {
        Map<String, Object> postgresql = (Map<String, Object>) config.get("postgresql");
        return (String) postgresql.get("database");
    }

    @SuppressWarnings("unchecked")
    public String getPostgresUser() {
        Map<String, Object> postgresql = (Map<String, Object>) config.get("postgresql");
        return (String) postgresql.get("user");
    }

    @SuppressWarnings("unchecked")
    public String getPostgresPassword() {
        Map<String, Object> postgresql = (Map<String, Object>) config.get("postgresql");
        return (String) postgresql.get("password");
    }

    @SuppressWarnings("unchecked")
    public List<String> getScales() {
        Map<String, Object> benchmark = (Map<String, Object>) config.get("benchmark");
        return (List<String>) benchmark.get("scales");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getScaleConfig(String scale) {
        Map<String, Object> benchmark = (Map<String, Object>) config.get("benchmark");
        Map<String, Object> customScales = (Map<String, Object>) benchmark.get("custom_scales");
        return (Map<String, Object>) customScales.get(scale);
    }

    @SuppressWarnings("unchecked")
    public List<Integer> getConcurrencyLevels() {
        Map<String, Object> benchmark = (Map<String, Object>) config.get("benchmark");
        return (List<Integer>) benchmark.get("concurrency_levels");
    }

    @SuppressWarnings("unchecked")
    public int getIterations() {
        Map<String, Object> benchmark = (Map<String, Object>) config.get("benchmark");
        return (Integer) benchmark.get("iterations");
    }

    @SuppressWarnings("unchecked")
    public int getWarmupOperations() {
        Map<String, Object> benchmark = (Map<String, Object>) config.get("benchmark");
        return (Integer) benchmark.get("warmup_operations");
    }

    @SuppressWarnings("unchecked")
    public int getTotalOperations() {
        Map<String, Object> benchmark = (Map<String, Object>) config.get("benchmark");
        return (Integer) benchmark.get("total_operations");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getConnectionPoolConfig() {
        Map<String, Object> benchmark = (Map<String, Object>) config.get("benchmark");
        return (Map<String, Object>) benchmark.get("connection_pool");
    }

    @SuppressWarnings("unchecked")
    public List<String> getEnabledScenarios() {
        Map<String, Object> benchmark = (Map<String, Object>) config.get("benchmark");
        List<String> scenarios = (List<String>) benchmark.get("enabled_scenarios");
        if (scenarios == null || scenarios.isEmpty()) {
            return List.of("scenario1", "scenario2", "scenario3", "scenario4");
        }
        return scenarios;
    }

    @SuppressWarnings("unchecked")
    public String getScenarioMongoUri(String scenario) {
        Map<String, Object> scenarios = (Map<String, Object>) config.get("scenarios");
        Map<String, Object> scenarioConfig = (Map<String, Object>) scenarios.get(scenario);
        return (String) scenarioConfig.get("mongodb_uri");
    }

    @SuppressWarnings("unchecked")
    public String getScenarioPostgresHost(String scenario) {
        Map<String, Object> scenarios = (Map<String, Object>) config.get("scenarios");
        Map<String, Object> scenarioConfig = (Map<String, Object>) scenarios.get(scenario);
        return (String) scenarioConfig.get("postgres_host");
    }
}
