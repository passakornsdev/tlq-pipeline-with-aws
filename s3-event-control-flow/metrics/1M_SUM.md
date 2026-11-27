| function\_name | configured\_memory\_mb | count\(function\_name\) | min\(runtime\) | avg\(runtime\) | max\(runtime\) |
| :--- | ---: | ---: | ---: | ---: | ---: |
| s3-csv-batch-processor-dispatcher | 1769 | 1 | 44058 | 44058.0000 | 44058 |
| s3-csv-batch-processor-loader | 640 | 1000 | 673 | 4265.3770 | 9033 |
| s3-csv-batch-processor-processor | 640 | 10 | 34884 | 36439.3000 | 37937 |
| s3-csv-batch-processor-query | 1024 | 1 | 5545 | 5545.0000 | 5545 |


##### Total Pipeline Execution Time: 95246ms

### Estimated Lambda Cost (USD)

Using x86 on-demand rates in `us-east-2` ([AWS Lambda pricing](https://aws.amazon.com/lambda/pricing/)): $0.0000166667 per GB-second and $0.20 per million requests. Compute uses recorded SAAF runtime and configured memory; amounts are before free tier, discounts, and credits.

| compute | requests | total |
| ---: | ---: | ---: |
| $0.04958782 | $0.00020240 | $0.04979022 |

### S3 Event and Loader Latency

| min\(s3\_latency\_ms\) | avg\(s3\_latency\_ms\) | max\(s3\_latency\_ms\) |
| :--- | :--- | :--- |
| 385 | 1594.9200 | 4777 |


### RDS Metrics

![img.png](img/1M/img_5.png)

![img_1.png](img/1M/img_4.png)

![img_2.png](img/1M/img_3.png)