| function\_name | configured\_memory\_mb | count\(function\_name\) | min\(runtime\) | avg\(runtime\) | max\(runtime\) |
| :--- | ---: | ---: | ---: | ---: | ---: |
| s3-sqs-event-control-flow-dispatcher | 1769 | 1 | 1727 | 1727.0000 | 1727 |
| s3-sqs-event-control-flow-loader | 640 | 10 | 975 | 2719.3000 | 3117 |
| s3-sqs-event-control-flow-processor | 640 | 1 | 6357 | 6357.0000 | 6357 |
| s3-sqs-event-control-flow-query | 1024 | 1 | 1224 | 1224.0000 | 1224 |


`configured_memory_mb` reflects current Terraform allocations, not measured peak memory or necessarily the allocation used when this run was captured.

### Total pipeline execution time (ms)

| total\_pipeline\_time\_ms |
| :--- |
| 23545 |


### SQS latency (ms)

| min\(sqs\_latency\_ms\) | avg\(sqs\_latency\_ms\) | max\(sqs\_latency\_ms\) |
| :--- | :--- | :--- |
| 2909 | 4298.3000 | 6569 |


### RDS Metrics

![img.png](rds-metrics/10k/img_9.png)

![img_1.png](rds-metrics/10k/img_10.png)

![img_2.png](rds-metrics/10k/img_11.png)