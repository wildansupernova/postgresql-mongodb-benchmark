# Benchmark Results

**Scenario:** scenario4
**Scale:** micro
**Concurrency:** 10

| Operation | Metric | PostgreSQL | MongoDB |
|-----------|--------|------------|---------|\n| fetch_filtered | throughput_ops_sec | 2000.00 | 2222.22 |
| fetch_filtered | p50_ms | 4.00 | 4.00 |
| fetch_filtered | p95_ms | 7.00 | 6.00 |
| fetch_filtered | p99_ms | 8.00 | 7.00 |
| count | throughput_ops_sec | 3448.28 | 2500.00 |
| count | p50_ms | 3.00 | 3.00 |
| count | p95_ms | 4.00 | 7.00 |
| count | p99_ms | 5.00 | 9.00 |
| insert | throughput_ops_sec | 414.94 | 287.97 |
| insert | p50_ms | 14.00 | 12.00 |
| insert | p95_ms | 27.00 | 16.00 |
| insert | p99_ms | 31.00 | 112.00 |
| update | throughput_ops_sec | 0.00 | 111.11 |
| update | p50_ms | 0.00 | 4.00 |
| update | p95_ms | 0.00 | 6.00 |
| update | p99_ms | 0.00 | 6.00 |
| batch_insert | throughput_ops_sec | 10.53 | 20.41 |
| batch_insert | p50_ms | 68.00 | 31.00 |
| batch_insert | p95_ms | 68.00 | 31.00 |
| batch_insert | p99_ms | 68.00 | 31.00 |
| fetch_order | throughput_ops_sec | 0.00 | 191.49 |
| fetch_order | p50_ms | 0.00 | 1.00 |
| fetch_order | p95_ms | 0.00 | 2.00 |
| fetch_order | p99_ms | 0.00 | 2.00 |
| delete | throughput_ops_sec | 0.00 | 163.64 |
| delete | p50_ms | 0.00 | 3.00 |
| delete | p95_ms | 0.00 | 4.00 |
| delete | p99_ms | 0.00 | 4.00 |
| batch_fetch | throughput_ops_sec | 0.00 | 0.00 |
| batch_fetch | p50_ms | 0.00 | 0.00 |
| batch_fetch | p95_ms | 0.00 | 0.00 |
| batch_fetch | p99_ms | 0.00 | 0.00 |
| append | throughput_ops_sec | 1923.08 | 636.94 |
| append | p50_ms | 5.00 | 14.00 |
| append | p95_ms | 7.00 | 30.00 |
| append | p99_ms | 8.00 | 30.00 |
| aggregate | throughput_ops_sec | 3571.43 | 5263.16 |
| aggregate | p50_ms | 3.00 | 2.00 |
| aggregate | p95_ms | 3.00 | 3.00 |
| aggregate | p99_ms | 4.00 | 3.00 |
