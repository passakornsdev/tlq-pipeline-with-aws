output "source_bucket_name" {
  description = "CSV upload bucket name."
  value       = aws_s3_bucket.source.bucket
}

output "destination_bucket_name" {
  description = "JSON batch output bucket name."
  value       = aws_s3_bucket.destination.bucket
}

output "dispatcher_lambda_name" {
  description = "Name of the S3 event dispatcher Lambda function."
  value       = aws_lambda_function.dispatcher.function_name
}

output "processor_lambda_name" {
  description = "Name of the batch processor Lambda function."
  value       = aws_lambda_function.processor.function_name
}

output "aurora_endpoint" {
  description = "Aurora MySQL writer endpoint."
  value       = aws_rds_cluster.aurora.endpoint
}

output "aurora_secret_arn" {
  description = "ARN of the Secrets Manager secret containing the Aurora master credentials."
  value       = aws_rds_cluster.aurora.master_user_secret[0].secret_arn
}
