| function\_name | configured\_memory\_mb | count\(function\_name\) | min\(runtime\) | avg\(runtime\) | max\(runtime\) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| s3-csv-batch-processor-dispatcher | 1769 | 1 | 216781 | 216781.0000 | 216781 |
| s3-csv-batch-processor-loader | 640 | 5000 | 665 | 9068.4114 | 22347 |
| s3-csv-batch-processor-processor | 640 | 50 | 30110 | 36075.8200 | 40185 |
| s3-csv-batch-processor-query | 1024 | 1 | 24349 | 24349.0000 | 24349 |


##### Total Pipeline Execution Time: 501300ms


### Estimated Lambda Cost (USD)

Using x86 on-demand rates in `us-east-2` ([AWS Lambda pricing](https://aws.amazon.com/lambda/pricing/)): $0.0000166667 per GB-second and $0.20 per million requests. Compute uses recorded SAAF runtime and configured memory; amounts are before free tier, discounts, and credits.

| compute | requests | total |
| ---: | ---: | ---: |
| $0.49775102 | $0.00101040 | $0.49876142 |

### S3 Event and Loader Latency

| min\(s3\_latency\_ms\) | avg\(s3\_latency\_ms\) | max\(s3\_latency\_ms\) |
| :--- | :--- | :--- |
| 340 | 52976.7206 | 247169 |

### RDS Metrics

![img.png](rds-metrics/5M/img.png)

![img_1.png](rds-metrics/5M/img_1.png)

![img_2.png](rds-metrics/5M/img_2.png)