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

variable "destination_bucket_name" {
  description = "JSON destination bucket name."
  type        = string
}

variable "destination_bucket_arn" {
  description = "JSON destination bucket ARN."
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

variable "db_user" {
  description = "Aurora database username for the loader Lambda."
  type        = string
}

variable "db_password" {
  description = "Aurora database password for the loader Lambda."
  type        = string
  sensitive   = true
}

variable "lambda_filename" {
  description = "Path to the Lambda deployment package."
  type        = string
}

variable "tags" {
  description = "Tags applied to Lambda resources."
  type        = map(string)
}
