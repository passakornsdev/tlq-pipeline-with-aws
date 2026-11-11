# AWS Deployment and Test Runbook

This guide deploys and tests the S3 CSV batch processor in `us-west-2`. Change the region consistently if you use another AWS region.

## Prerequisites

- Java 21 and Maven 3.9 or later
- Terraform 1.5 or later
- AWS CLI authenticated with a profile that can create S3, Lambda, IAM, and CloudWatch resources
- Two globally unique S3 bucket names

Confirm that the AWS CLI is using the intended account:

```bash
aws sts get-caller-identity --profile default --region us-west-2
```

## Configure Terraform

Create a local Terraform variables file from the checked-in example:

```bash
cd terraform
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` with your region and globally unique bucket base names:

```hcl
aws_region              = "us-west-2"
source_bucket_name      = "my-unique-csv-source"
destination_bucket_name = "my-unique-json-destination"
# db_password = "replace-with-a-secure-password"
```

Terraform appends the deployment creation date in `YYYYMMDD` format to each base name. For example, the source bucket created on November 8, 2026 is `my-unique-csv-source-20261108`. The date remains stable for the Terraform state after creation.
The loader connects through the Aurora RDS Proxy. The same `db_password` is used for the Aurora `admin` user, the proxy’s Secrets Manager credential, and the loader’s `DB_PASSWORD` environment variable. If the setting is omitted, Terraform prompts for it.

`terraform.tfvars` is ignored by Git, so account-specific bucket base names are not committed.


Build the Lambda and deploy AWS Infrastructure

```bash
mvn package

terraform -chdir=terraform init
terraform -chdir=terraform fmt -check
terraform -chdir=terraform validate

terraform -chdir=terraform plan -out=tfplan -input=false

terraform -chdir=terraform apply -input=false tfplan
```

Record the deployed resource names:

```bash
terraform -chdir=terraform output
```

## Exercise the full pipeline

Set shell variables from Terraform outputs and upload the supplied CSV fixture:

```bash
AWS_REGION="us-west-2"
SOURCE_BUCKET=$(terraform -chdir=terraform output -raw source_bucket_name)
DESTINATION_BUCKET=$(terraform -chdir=terraform output -raw destination_bucket_name)

aws s3 cp 1K_RECORDS.csv \
  "s3://${SOURCE_BUCKET}/1K_RECORDS.csv" \
  --region "${AWS_REGION}"
```

The source bucket event invokes the dispatcher. It counts data lines after the header and invokes the processor asynchronously for each group of at most 10,000 lines.

Wait briefly for the S3 and Lambda events, then list the generated JSON batches:

```bash
aws s3 ls "s3://${DESTINATION_BUCKET}/converted/" \
  --recursive \
  --region "${AWS_REGION}"
```

Each output is a JSON array with camelCase properties derived from the `SAMPLE.csv` header:

```json
[
  {
    "changeType": "NEW",
    "physicianProfileId": "59308",
    "physicianFirstName": "Seth",
    "physicianLastName": "Berk",
    "totalAmountInvestedUsDollars": "9000.00"
  }
]
```

## Inspect Lambda logs

Get the names of the deployed functions:

```bash
DISPATCHER_FUNCTION=$(terraform -chdir=terraform output -raw dispatcher_lambda_name)
PROCESSOR_FUNCTION=$(terraform -chdir=terraform output -raw processor_lambda_name)

aws logs tail /aws/lambda/${DISPATCHER_FUNCTION} --since 15m
aws logs tail /aws/lambda/${PROCESSOR_FUNCTION} --since 15m
```

The dispatcher logs report the source object’s data-line and batch counts. The processor logs report each destination JSON object and its record count.

The loader listens for `converted/*.json` objects and inserts each batch into Aurora. It creates the `payments_raw` and `loaded_destination_objects` tables on first load, uses the destination prefix as `run_id`, and skips duplicate notifications for an already-loaded object:

```bash
LOADER_FUNCTION=$(terraform -chdir=terraform output -raw loader_lambda_name)
aws logs tail /aws/lambda/${LOADER_FUNCTION} --since 15m
```

## Confirm Terraform state

After testing, verify the deployed AWS resources match the local configuration:

```bash
terraform -chdir=terraform plan -detailed-exitcode -input=false
```

An exit code of `0` and `No changes` means there is no drift.

## Clean up

The buckets are versioned, so delete all object versions and delete markers before destroying the infrastructure. Run the following for each bucket:

```bash
SOURCE_BUCKET=$(terraform -chdir=terraform output -raw source_bucket_name)
DESTINATION_BUCKET=$(terraform -chdir=terraform output -raw destination_bucket_name)

for BUCKET in "${SOURCE_BUCKET}" "${DESTINATION_BUCKET}"; do
  aws s3api list-object-versions \
    --bucket "${BUCKET}" \
    --query '{Objects: [Versions[].{Key: Key, VersionId: VersionId}, DeleteMarkers[].{Key: Key, VersionId: VersionId}][]}' \
    --output json \
    --region "${AWS_REGION}" > "/tmp/${BUCKET}-objects.json"

  if [ "$(jq '.Objects | length' "/tmp/${BUCKET}-objects.json")" -gt 0 ]; then
    aws s3api delete-objects \
      --bucket "${BUCKET}" \
      --delete "file:///tmp/${BUCKET}-objects.json" \
      --region "${AWS_REGION}"
  fi

  rm "/tmp/${BUCKET}-objects.json"
done
```

The cleanup command requires `jq`, which is available by default on current macOS systems. For buckets with more than 1,000 versions or delete markers, repeat the command until the bucket is empty.

Then destroy the deployed infrastructure:

```bash
terraform -chdir=terraform destroy
```
