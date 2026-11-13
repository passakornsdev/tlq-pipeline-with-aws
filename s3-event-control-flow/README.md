# S3 CSV Batch Processor

Terraform deploys two S3 buckets and three Java 21 Lambda functions:

1. Uploading a `.csv` file to the source bucket invokes the dispatcher.
2. The dispatcher counts physical data lines after the header and asynchronously invokes the processor once per 10,000 lines.
3. Each processor converts its assigned lines to a JSON array and stores the result in the destination bucket.
4. A loader Lambda reads each generated JSON batch and inserts its records into Aurora MySQL.
5. After the final successful load, the query Lambda emails an aggregate CSV report through SES.

## Prerequisites

- Java 21 and Maven 3.9+
- Terraform 1.5+
- AWS credentials configured for the selected region

## Deploy & Test
Check AWS_RUNBOOK.md, Please use IntelliJ as it has a feature to render and execute code block so you won't have a hard time copying commands.

You can just click on green double arrow button

![img.png](img.png)

### SES prerequisites

Before deploying the query email flow:

- Verify `SES_FROM_EMAIL` or its domain in the same AWS Region used by the query Lambda.
- If the SES account is still in the sandbox, verify `SES_TO_EMAIL` too, or request production access to send to unverified recipients.
- Confirm the query Lambda role has `ses:SendRawEmail`.
- Confirm that the SES interface endpoint is available before sending mail. Terraform creates the `com.amazonaws.<region>.email` interface endpoint for the query Lambda.

## Input and output contract

Input CSV files must use a first-row header. The dispatcher uses **physical newline-delimited lines**: quoted CSV fields must not contain embedded newlines. Every data line must contain the same number of columns as the header.

Each processor writes one JSON array to:

```text
converted/<source-version-or-event-sequencer>/<source-key>/batch-<index>.json
```

The version/event-qualified prefix ensures re-uploading the same source key does not overwrite previous output. Re-delivery of the exact same S3 event writes to the same output key, making the output idempotent.

The loader listens for `.json` objects under `converted/`. It creates `pipeline_info`, `payments_raw`, and `loaded_destination_objects` if they do not exist, stores the destination prefix as `run_id`, and skips a destination object that has already been loaded. The dispatcher records the expected batch count in `pipeline_info`; successful new loads increment the finished count, and the final load invokes the query Lambda.

## Clean up

Terraform does not delete non-empty buckets. Remove the test objects first then execute destroy command:

```bash
aws s3 rm "s3://$(terraform output -raw source_bucket_name)" --recursive --region us-east-2
aws s3 rm "s3://$(terraform output -raw destination_bucket_name)" --recursive --region us-east-2
  
cd terraform
terraform destroy
```
