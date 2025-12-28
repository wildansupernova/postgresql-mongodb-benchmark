package com.benchmark.results;

import com.benchmark.metrics.MetricsResult;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

public class ResultsWriter {
    private final String outputDir;

    public ResultsWriter(String baseDir) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        this.outputDir = baseDir;
        try {
            Files.createDirectories(Paths.get(outputDir));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create output directory", e);
        }
    }

    public void writeResults(BenchmarkResults results) {
        writeCsv(results);
        writeMarkdown(results);
    }

    private void writeCsv(BenchmarkResults results) {
        String filename = String.format("%s/results.csv", outputDir);
        
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write("operation,metric,postgresql,mongodb\n");
            
            for (Map.Entry<String, BenchmarkResults.DatabaseResults> entry : results.getOperationResults().entrySet()) {
                String operation = entry.getKey();
                Map<String, MetricsResult> dbResults = entry.getValue().getResults();
                
                MetricsResult pgResult = dbResults.get("postgresql");
                MetricsResult mongoResult = dbResults.get("mongodb");
                
                if (pgResult != null && mongoResult != null) {
                    writer.write(String.format(Locale.US, "%s,throughput_ops_sec,%.2f,%.2f\n", operation, pgResult.getThroughputOpsSec(), mongoResult.getThroughputOpsSec()));
                    writer.write(String.format(Locale.US, "%s,p50_ms,%.2f,%.2f\n", operation, pgResult.getP50Ms(), mongoResult.getP50Ms()));
                    writer.write(String.format(Locale.US, "%s,p95_ms,%.2f,%.2f\n", operation, pgResult.getP95Ms(), mongoResult.getP95Ms()));
                    writer.write(String.format(Locale.US, "%s,p99_ms,%.2f,%.2f\n", operation, pgResult.getP99Ms(), mongoResult.getP99Ms()));
                    writer.write(String.format(Locale.US, "%s,avg_ms,%.2f,%.2f\n", operation, pgResult.getAvgMs(), mongoResult.getAvgMs()));
                    writer.write(String.format(Locale.US, "%s,min_ms,%d,%d\n", operation, pgResult.getMinMs(), mongoResult.getMinMs()));
                    writer.write(String.format(Locale.US, "%s,max_ms,%d,%d\n", operation, pgResult.getMaxMs(), mongoResult.getMaxMs()));
                }
            }
            
            System.out.println("CSV results written to: " + filename);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write CSV results", e);
        }
    }

    private void writeMarkdown(BenchmarkResults results) {
        String filename = String.format("%s/results.md", outputDir);
        
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(String.format("# Benchmark Results\n\n"));
            writer.write(String.format("**Scenario:** %s\n", results.getScenario()));
            writer.write(String.format("**Scale:** %s\n", results.getScale()));
            writer.write(String.format("**Concurrency:** %d\n\n", results.getConcurrency()));
            
            writer.write("| Operation | Metric | PostgreSQL | MongoDB |\n");
            writer.write("|-----------|--------|------------|---------|\\n");
            
            for (Map.Entry<String, BenchmarkResults.DatabaseResults> entry : results.getOperationResults().entrySet()) {
                String operation = entry.getKey();
                Map<String, MetricsResult> dbResults = entry.getValue().getResults();
                
                MetricsResult pgResult = dbResults.get("postgresql");
                MetricsResult mongoResult = dbResults.get("mongodb");
                
                if (pgResult != null && mongoResult != null) {
                    writer.write(String.format(Locale.US, "| %s | throughput_ops_sec | %.2f | %.2f |\n", operation, pgResult.getThroughputOpsSec(), mongoResult.getThroughputOpsSec()));
                    writer.write(String.format(Locale.US, "| %s | p50_ms | %.2f | %.2f |\n", operation, pgResult.getP50Ms(), mongoResult.getP50Ms()));
                    writer.write(String.format(Locale.US, "| %s | p95_ms | %.2f | %.2f |\n", operation, pgResult.getP95Ms(), mongoResult.getP95Ms()));
                    writer.write(String.format(Locale.US, "| %s | p99_ms | %.2f | %.2f |\n", operation, pgResult.getP99Ms(), mongoResult.getP99Ms()));
                }
            }
            
            System.out.println("Markdown results written to: " + filename);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Markdown results", e);
        }
    }
}
