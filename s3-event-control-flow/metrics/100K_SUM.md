| function\_name | configured\_memory\_mb | count\(function\_name\) | min\(runtime\) | avg\(runtime\) | max\(runtime\) |
| :--- | ---: | ---: | ---: | ---: | ---: |
| s3-csv-batch-processor-dispatcher | 1769 | 2 | 5815 | 6181.5000 | 6548 |
| s3-csv-batch-processor-loader | 640 | 100 | 701 | 1414.8800 | 3731 |
| s3-csv-batch-processor-processor | 640 | 1 | 38325 | 38325.0000 | 38325 |
| s3-csv-batch-processor-query | 1024 | 1 | 1762 | 1762.0000 | 1762 |

### Total Pipeline Execution Time

| total\_pipeline\_time\_ms | total execution time |
| :--- | :--- |
| 54104 | 54.104 seconds |


### S3 Event and Loader Latency

| min\(s3\_latency\_ms\) | avg\(s3\_latency\_ms\) | max\(s3\_latency\_ms\) |
| :--- | :--- | :--- |
| 463 | 1552.1100 | 4927 |


### RDS Metrics

![img.png](img/100K/img.png)

![img_2.png](img/100K/img_2.png)

![img_1.png](img/100K/img_1.png)