package com.mrscrape.benchmark.config;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

@Command(name = "benchmark", description = "MongoDB vs PostgreSQL Benchmark Tool",
         version = "1.0.0", mixinStandardHelpOptions = true)
public class BenchmarkConfig implements Runnable {

    @Option(names = {"--mode"}, description = "Mode: measurement or aggregation", required = true)
    private String mode;

    @Option(names = {"--scenario"}, description = "Scenario: 1 or 2 (required for measurement mode)")
    private Integer scenario;

    @Option(names = {"--database"}, description = "Database: mongodb or postgresql (required for measurement mode)")
    private String database;

    @Option(names = {"--concurrency"}, description = "Number of concurrent virtual threads (required for measurement mode)")
    private Integer concurrency;

    @Option(names = {"--insert-count"}, description = "Number of insert operations (required for measurement mode)")
    private Integer insertCount;

    @Option(names = {"--update-modify-count"}, description = "Number of update-modify operations (required for measurement mode)")
    private Integer updateModifyCount;

    @Option(names = {"--update-add-count"}, description = "Number of update-add operations (required for measurement mode)")
    private Integer updateAddCount;

    @Option(names = {"--query-count"}, description = "Number of query operations (required for measurement mode)")
    private Integer queryCount;

    @Option(names = {"--delete-count"}, description = "Number of delete operations (required for measurement mode)")
    private Integer deleteCount;

    @Option(names = {"--connection-string"}, description = "Database connection string (required for measurement mode)")
    private String connectionString;

    @Option(names = {"--output-file"}, description = "Output file path (required for both modes)")
    private String outputFile;

    @Option(names = {"--input-files"}, description = "Comma-separated CSV files to aggregate (required for aggregation mode)")
    private String inputFiles;

    public void validate() {
        if (mode == null || mode.isEmpty()) {
            throw new ParameterException(null, "--mode is required");
        }

        if (!mode.equalsIgnoreCase("measurement") && !mode.equalsIgnoreCase("aggregation")) {
            throw new ParameterException(null, "--mode must be 'measurement' or 'aggregation'");
        }

        if (outputFile == null || outputFile.isEmpty()) {
            throw new ParameterException(null, "--output-file is required");
        }

        if (mode.equalsIgnoreCase("measurement")) {
            if (scenario == null || (scenario != 1 && scenario != 2)) {
                throw new ParameterException(null, "--scenario is required for measurement mode and must be 1 or 2");
            }
            if (database == null || database.isEmpty()) {
                throw new ParameterException(null, "--database is required for measurement mode");
            }
            if (!database.equalsIgnoreCase("mongodb") && !database.equalsIgnoreCase("postgresql")) {
                throw new ParameterException(null, "--database must be 'mongodb' or 'postgresql'");
            }
            if (concurrency == null || concurrency <= 0) {
                throw new ParameterException(null, "--concurrency is required for measurement mode and must be > 0");
            }
            if (insertCount == null || insertCount < 0) {
                throw new ParameterException(null, "--insert-count is required for measurement mode");
            }
            if (updateModifyCount == null || updateModifyCount < 0) {
                throw new ParameterException(null, "--update-modify-count is required for measurement mode");
            }
            if (updateAddCount == null || updateAddCount < 0) {
                throw new ParameterException(null, "--update-add-count is required for measurement mode");
            }
            if (queryCount == null || queryCount < 0) {
                throw new ParameterException(null, "--query-count is required for measurement mode");
            }
            if (deleteCount == null || deleteCount < 0) {
                throw new ParameterException(null, "--delete-count is required for measurement mode");
            }
            if (connectionString == null || connectionString.isEmpty()) {
                throw new ParameterException(null, "--connection-string is required for measurement mode");
            }
        } else if (mode.equalsIgnoreCase("aggregation")) {
            if (inputFiles == null || inputFiles.isEmpty()) {
                throw new ParameterException(null, "--input-files is required for aggregation mode");
            }
        }
    }

    // Getters
    public String getMode() {
        return mode;
    }

    public Integer getScenario() {
        return scenario;
    }

    public String getDatabase() {
        return database;
    }

    public Integer getConcurrency() {
        return concurrency;
    }

    public Integer getInsertCount() {
        return insertCount;
    }

    public Integer getUpdateModifyCount() {
        return updateModifyCount;
    }

    public Integer getUpdateAddCount() {
        return updateAddCount;
    }

    public Integer getQueryCount() {
        return queryCount;
    }

    public Integer getDeleteCount() {
        return deleteCount;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public String getInputFiles() {
        return inputFiles;
    }

    // Setters for testing
    public void setMode(String mode) {
        this.mode = mode;
    }

    public void setScenario(Integer scenario) {
        this.scenario = scenario;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public void setConcurrency(Integer concurrency) {
        this.concurrency = concurrency;
    }

    public void setInsertCount(Integer insertCount) {
        this.insertCount = insertCount;
    }

    public void setUpdateModifyCount(Integer updateModifyCount) {
        this.updateModifyCount = updateModifyCount;
    }

    public void setUpdateAddCount(Integer updateAddCount) {
        this.updateAddCount = updateAddCount;
    }

    public void setQueryCount(Integer queryCount) {
        this.queryCount = queryCount;
    }

    public void setDeleteCount(Integer deleteCount) {
        this.deleteCount = deleteCount;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    public void setInputFiles(String inputFiles) {
        this.inputFiles = inputFiles;
    }

    @Override
    public void run() {
    }
}
