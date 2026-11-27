| function\_name | configured\_memory\_mb | count\(function\_name\) | min\(runtime\) | avg\(runtime\) | max\(runtime\) |
| :--- | ---: | ---: | ---: | ---: | ---: |
| s3-sqs-event-control-flow-dispatcher | 1769 | 1 | 5790 | 5790.0000 | 5790 |
| s3-sqs-event-control-flow-loader | 640 | 100 | 584 | 1031.2500 | 3059 |
| s3-sqs-event-control-flow-processor | 640 | 1 | 50363 | 50363.0000 | 50363 |
| s3-sqs-event-control-flow-query | 1024 | 1 | 1754 | 1754.0000 | 1754 |


`configured_memory_mb` reflects current Terraform allocations, not measured peak memory or necessarily the allocation used when this run was captured.

##### Total pipeline execution time: 65449ms

### Estimated Lambda cost (USD)

Estimate uses current configured memory, SAAF average runtime, x86\_64 in us-east-2, and on-demand rates of $0.0000166667 per GB-second plus $0.20 per million requests. It excludes free tier, discounts, and non-Lambda charges such as SQS, S3, RDS, and CloudWatch Logs.

| compute cost | request cost | total Lambda cost |
| ---: | ---: | ---: |
| $0.00179478 | $0.00002060 | $0.00181538 |


### SQS latency (ms)

| min\(sqs\_latency\_ms\) | avg\(sqs\_latency\_ms\) | max\(sqs\_latency\_ms\) |
| :--- | :--- | :--- |
| 250 | 764.6900 | 5950 |


### RDS Metrics

![img.png](rds-metrics/100k/img.png)

![img_2.png](rds-metrics/100k/img_2.png)

![img_1.png](rds-metrics/100k/img_1.png)