output "source_bucket_name" {
  description = "CSV upload bucket name."
  value       = module.s3.source_bucket_name
}

output "destination_bucket_name" {
  description = "JSON batch output bucket name."
  value       = module.s3.destination_bucket_name
}

output "dispatcher_lambda_name" {
  description = "Name of the S3 event dispatcher Lambda function."
  value       = module.lambda.dispatcher_lambda_name
}

output "processor_lambda_name" {
  description = "Name of the batch processor Lambda function."
  value       = module.lambda.processor_lambda_name
}

output "aurora_endpoint" {
  description = "Aurora MySQL writer endpoint."
  value       = module.aurora.endpoint
}

output "aurora_secret_arn" {
  description = "ARN of the Secrets Manager secret containing the Aurora master credentials."
  value       = module.aurora.secret_arn
}
