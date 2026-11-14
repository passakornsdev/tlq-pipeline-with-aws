variable "project_name" {
  description = "Project name used in resource names."
  type        = string
}

variable "source_bucket_name" {
  description = "CSV source bucket name."
  type        = string
}

variable "source_bucket_arn" {
  description = "CSV source bucket ARN."
  type        = string
}

variable "sqs_queue_url" {
  description = "SQS queue URL for processed batches."
  type        = string
}

variable "sqs_queue_arn" {
  description = "SQS queue ARN for processed batches."
  type        = string
}

variable "subnet_ids" {
  description = "Subnets for VPC-connected Lambda functions."
  type        = list(string)
}

variable "lambda_security_group_id" {
  description = "Security group for the Lambda functions."
  type        = string
}

variable "db_host" {
  description = "Aurora endpoint for the loader Lambda."
  type        = string
}

variable "db_port" {
  description = "Aurora port for the loader Lambda."
  type        = number
}

variable "db_name" {
  description = "Aurora database name for the loader Lambda."
  type        = string
}

variable "db_secret_arn" {
  description = "Secrets Manager ARN containing the Aurora credentials."
  type        = string
}

variable "ses_from_email" {
  description = "Verified SES sender email address."
  type        = string
}

variable "ses_to_email" {
  description = "SES recipient email address."
  type        = string
}

variable "lambda_filename" {
  description = "Path to the Lambda deployment package."
  type        = string
}

variable "tags" {
  description = "Tags applied to Lambda resources."
  type        = map(string)
}
