# S3 CSV Batch Processor

Terraform deploys two S3 buckets and two Java 21 Lambda functions:

1. Uploading a `.csv` file to the source bucket invokes the dispatcher.
2. The dispatcher counts physical data lines after the header and asynchronously invokes the processor once per 10,000 lines.
3. Each processor converts its assigned lines to a JSON array and stores the result in the destination bucket.
4. A loader Lambda reads each generated JSON batch and inserts its records into Aurora MySQL.

## Prerequisites

- Java 21 and Maven 3.9+
- Terraform 1.5+
- AWS credentials configured for the selected region

## Deploy

```bash
mvn package
cd terraform
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with globally unique bucket base names.
terraform init
terraform apply
```

Terraform reads `target/s3-event-control-flow-lambda.jar`, so run `mvn package` before the first apply and after changing Java source.
Terraform appends the initial deployment date in `YYYYMMDD` format to each bucket base name.

Set `db_password` in `terraform.tfvars` (or provide it interactively) to the password used by Aurora and the RDS Proxy. The loader receives the RDS Proxy endpoint through `DB_HOST`, along with `DB_PORT`, `DB_NAME`, `DB_USER`, and `DB_PASSWORD` through its Lambda environment.

## Input and output contract

Input CSV files must use a first-row header. The dispatcher uses **physical newline-delimited lines**: quoted CSV fields must not contain embedded newlines. Every data line must contain the same number of columns as the header.

Each processor writes one JSON array to:

```text
converted/<source-version-or-event-sequencer>/<source-key>/batch-<index>.json
```

The version/event-qualified prefix ensures re-uploading the same source key does not overwrite previous output. Re-delivery of the exact same S3 event writes to the same output key, making the output idempotent.

The loader listens for `.json` objects under `converted/`. It creates `payments_raw` and `loaded_destination_objects` if they do not exist, stores the destination prefix as `run_id`, and skips a destination object that has already been loaded.

## Clean up

Terraform does not delete non-empty buckets. Remove the test objects first then execute destroy command:

```bash
aws s3 rm "s3://$(terraform output -raw source_bucket_name)" --recursive --region us-east-2
aws s3 rm "s3://$(terraform output -raw destination_bucket_name)" --recursive --region us-east-2
  
cd terraform
terraform destroy
```
