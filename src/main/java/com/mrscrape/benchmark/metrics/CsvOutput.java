package com.mrscrape.benchmark.metrics;

import java.io.*;
import java.util.*;

public class CsvOutput {
    private static final String DELIMITER = ";";

    public static void writeMeasurementResults(String outputFile, MetricsCollector collector) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            for (String operationName : collector.getOperationNames()) {
                writeMetric(writer, operationName + "_throughput_ops_per_sec", 
                        String.valueOf(collector.getThroughput(operationName)));
                writeMetric(writer, operationName + "_avg_latency_ms", 
                        String.format("%.2f", collector.getAverageDuration(operationName)));
                writeMetric(writer, operationName + "_p50_latency_ms", 
                        String.valueOf(collector.getP50(operationName)));
                writeMetric(writer, operationName + "_p75_latency_ms", 
                        String.valueOf(collector.getP75(operationName)));
                writeMetric(writer, operationName + "_p99_latency_ms", 
                        String.valueOf(collector.getP99(operationName)));
            }
        }
    }

    private static void writeMetric(PrintWriter writer, String metricName, String value) {
        writer.println(metricName + DELIMITER + value);
    }

    public static Map<String, Map<String, Double>> readMeasurementFiles(List<String> filePaths) throws IOException {
        Map<String, Map<String, Double>> result = new LinkedHashMap<>();

        for (String filePath : filePaths) {
            Map<String, Double> fileMetrics = new LinkedHashMap<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] parts = line.split(DELIMITER);
                    if (parts.length == 2) {
                        String value = parts[1].trim().replace(",", ".");
                        fileMetrics.put(parts[0].trim(), Double.parseDouble(value));
                    }
                }
            }
            result.put(filePath, fileMetrics);
        }

        return result;
    }

    public static void writeAggregationResults(String outputFile, List<String> inputFiles, 
                                               Map<String, Map<String, Double>> allMetrics) throws IOException {
        if (inputFiles.isEmpty() || allMetrics.isEmpty()) {
            throw new IllegalArgumentException("No input files or metrics provided");
        }

        Set<String> allMetricKeys = new LinkedHashSet<>();
        for (Map<String, Double> metrics : allMetrics.values()) {
            allMetricKeys.addAll(metrics.keySet());
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            StringBuilder headerBuilder = new StringBuilder("metric_name");
            for (String filePath : inputFiles) {
                headerBuilder.append(DELIMITER).append(filePath);
            }
            writer.println(headerBuilder.toString());

            for (String metricKey : allMetricKeys) {
                StringBuilder rowBuilder = new StringBuilder(metricKey);
                for (String filePath : inputFiles) {
                    Map<String, Double> metrics = allMetrics.get(filePath);
                    Double value = metrics != null ? metrics.get(metricKey) : null;
                    rowBuilder.append(DELIMITER);
                    if (value != null) {
                        rowBuilder.append(String.format("%.2f", value));
                    } else {
                        rowBuilder.append("N/A");
                    }
                }
                writer.println(rowBuilder.toString());
            }
        }
    }
}
