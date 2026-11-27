| function\_name | configured\_memory\_mb | count\(function\_name\) | min\(runtime\) | avg\(runtime\) | max\(runtime\) |
| :--- | ---: | ---: | ---: | ---: | ---: |
| s3-sqs-event-control-flow-dispatcher | 1769 | 1 | 215487 | 215487.0000 | 215487 |
| s3-sqs-event-control-flow-loader | 640 | 5000 | 582 | 27231.2614 | 226927 |
| s3-sqs-event-control-flow-processor | 640 | 50 | 42558 | 47917.0800 | 51148 |
| s3-sqs-event-control-flow-query | 1024 | 1 | 52450 | 52450.0000 | 52450 |

`configured_memory_mb` reflects current Terraform allocations, not measured peak memory or necessarily the allocation used when this run was captured.

### SQS latency (ms)

| min\(sqs\_latency\_ms\) | avg\(sqs\_latency\_ms\) | max\(sqs\_latency\_ms\) |
| :--- | :--- | :--- |
| 408 | 140493.4216 | 356709 |


### Database Metrics

![img_2.png](rds-metrics/img_2.png)

![img.png](rds-metrics/img.png)

![img_1.png](rds-metrics/img_1.png)