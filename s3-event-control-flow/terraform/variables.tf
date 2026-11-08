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
