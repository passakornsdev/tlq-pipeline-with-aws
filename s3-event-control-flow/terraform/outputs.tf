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

output "loader_lambda_name" {
  description = "Name of the destination JSON loader Lambda function."
  value       = module.lambda.loader_lambda_name
}

output "aurora_endpoint" {
  description = "Aurora MySQL writer endpoint."
  value       = module.aurora.endpoint
}

output "aurora_proxy_endpoint" {
  description = "Aurora RDS Proxy endpoint used by the loader Lambda."
  value       = module.aurora.proxy_endpoint
}

output "aurora_secret_arn" {
  description = "ARN of the Aurora-managed Secrets Manager database credentials secret."
  value       = module.aurora.secret_arn
}
