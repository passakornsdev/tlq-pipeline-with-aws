variable "project_name" {
  description = "Project name used in resource names."
  type        = string
}

variable "instance_class" {
  description = "Aurora writer instance class."
  type        = string
}

variable "subnet_ids" {
  description = "Subnets for the Aurora DB subnet group."
  type        = list(string)
}

variable "vpc_id" {
  description = "VPC containing Aurora."
  type        = string
}

variable "lambda_security_group_id" {
  description = "Security group allowed to access Aurora."
  type        = string
}

variable "tags" {
  description = "Tags applied to Aurora resources."
  type        = map(string)
}
