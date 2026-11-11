variable "aws_region" {
  description = "AWS region in which to deploy the resources."
  type        = string
}

variable "project_name" {
  description = "Prefix used for Lambda function names and resource tags."
  type        = string
  default     = "s3-csv-batch-processor"
}

variable "source_bucket_name" {
  description = "Base name for the bucket receiving CSV uploads; Terraform appends the deployment date."
  type        = string

  validation {
    condition     = length(var.source_bucket_name) <= 54
    error_message = "source_bucket_name must be at most 54 characters to allow the -YYYYMMDD suffix."
  }
}

variable "destination_bucket_name" {
  description = "Base name for the bucket receiving JSON batch output; Terraform appends the deployment date."
  type        = string

  validation {
    condition     = length(var.destination_bucket_name) <= 54
    error_message = "destination_bucket_name must be at most 54 characters to allow the -YYYYMMDD suffix."
  }
}

variable "tags" {
  description = "Additional tags to apply to supported resources."
  type        = map(string)
  default     = {}
}

variable "aurora_instance_class" {
  description = "Instance class for the Aurora MySQL writer instance."
  type        = string
  default     = "db.t4g.large"
}

variable "db_port" {
  description = "Aurora MySQL port exposed to the loader Lambda."
  type        = number
  default     = 3306
}

variable "db_name" {
  description = "Aurora database name used by the loader Lambda."
  type        = string
  default     = "tlq"
}

variable "db_user" {
  description = "Aurora database username used by the loader Lambda."
  type        = string
  default     = "admin"
}

variable "db_password" {
  description = "Aurora database password used by the loader Lambda."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.db_password) >= 8
    error_message = "db_password must be at least 8 characters."
  }
}
