output "source_bucket_name" {
  description = "CSV upload bucket name."
  value       = module.s3.source_bucket_name
}

output "sqs_queue_url" {
  description = "SQS queue URL for processed batches."
  value       = module.sqs.queue_url
}

output "dispatcher_lambda_name" {
  description = "Name of the S3 event dispatcher Lambda function."
  value       = module.lambda.dispatcher_lambda_name
}

output "loader_lambda_name" {
  description = "Name of the SQS batch loader Lambda function."
  value       = module.lambda.loader_lambda_name
}

output "query_lambda_name" {
  description = "Name of the payment aggregate query Lambda function."
  value       = module.lambda.query_lambda_name
}

output "database_endpoint" {
  description = "Single-AZ MySQL RDS instance endpoint."
  value       = module.database.endpoint
}

output "database_proxy_endpoint" {
  description = "RDS Proxy endpoint used by the Lambda functions."
  value       = module.database.proxy_endpoint
}

output "database_secret_arn" {
  description = "ARN of the RDS-managed Secrets Manager database credentials secret."
  value       = module.database.secret_arn
}

output "bastion_instance_id" {
  description = "SSM target instance ID for MySQL port forwarding."
  value       = module.bastion.instance_id
}
