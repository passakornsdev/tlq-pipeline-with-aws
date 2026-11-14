# S3/SQS CSV Batch Processor

Terraform deploys an S3 source bucket, an SQS batch queue with a dead-letter queue, and four Java 25 Lambda functions:

1. Uploading a `.csv` file to the source bucket invokes the dispatcher.
2. The dispatcher counts physical data lines after the header and asynchronously invokes the processor once per 500 lines.
3. Each processor converts its assigned lines to `{ "header": [...], "rows": [[...], ...] }` and sends one message to SQS.
4. An SQS event source mapping invokes the loader once per message; the loader inserts its records into Aurora MySQL.
5. After the final successful load, the query Lambda emails an aggregate CSV report through SES.

## Prerequisites

- Java 25 and Maven 3.9+
- Terraform 1.5+
- AWS credentials configured for the selected region
- SES Identity (Both Sender and Receiver)

## Deploy & Test
Check AWS_RUNBOOK.md, Please use IntelliJ as it has a feature to render and execute code block so you won't have a hard time copying commands.

You can just click on green double arrow button

![img.png](img.png)

There is 1K records test data in the project, if you want more rows you can get it from https://faculty.washington.edu/wlloyd/courses/tcss562/project/tlq/medical_records_data/.

Put the file on the source bucket and you should receive an email with a CSV file.

### SES prerequisites

Before deploying the query email flow:

- Verify `SES_FROM_EMAIL` or its domain in the same AWS Region used by the query Lambda.
- If the SES account is still in the sandbox, verify `SES_TO_EMAIL` too, or request production access to send to unverified recipients.
- Confirm the query Lambda role has `ses:SendRawEmail`.
- Confirm that the SES interface endpoint is available before sending mail. Terraform creates the `com.amazonaws.<region>.email` interface endpoint for the query Lambda.