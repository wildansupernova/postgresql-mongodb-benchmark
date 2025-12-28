package com.benchmark.metrics;

public class MetricsResult {
    private final double p50Ms;
    private final double p95Ms;
    private final double p99Ms;
    private final double avgMs;
    private final long minMs;
    private final long maxMs;
    private final double throughputOpsSec;
    private final double cpuPercent;
    private final double memoryMb;

    public MetricsResult(double p50Ms, double p95Ms, double p99Ms, double avgMs, long minMs, long maxMs, 
                        double throughputOpsSec, double cpuPercent, double memoryMb) {
        this.p50Ms = p50Ms;
        this.p95Ms = p95Ms;
        this.p99Ms = p99Ms;
        this.avgMs = avgMs;
        this.minMs = minMs;
        this.maxMs = maxMs;
        this.throughputOpsSec = throughputOpsSec;
        this.cpuPercent = cpuPercent;
        this.memoryMb = memoryMb;
    }

    public double getP50Ms() {
        return p50Ms;
    }

    public double getP95Ms() {
        return p95Ms;
    }

    public double getP99Ms() {
        return p99Ms;
    }

    public double getAvgMs() {
        return avgMs;
    }

    public long getMinMs() {
        return minMs;
    }

    public long getMaxMs() {
        return maxMs;
    }

    public double getThroughputOpsSec() {
        return throughputOpsSec;
    }

    public double getCpuPercent() {
        return cpuPercent;
    }

    public double getMemoryMb() {
        return memoryMb;
    }

    @Override
    public String toString() {
        return String.format("p50=%.2fms, p95=%.2fms, p99=%.2fms, avg=%.2fms, throughput=%.2f ops/sec",
                p50Ms, p95Ms, p99Ms, avgMs, throughputOpsSec);
    }
}
