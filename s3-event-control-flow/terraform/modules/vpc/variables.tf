variable "aws_region" {
  description = "AWS region for VPC service endpoints."
  type        = string
}

variable "project_name" {
  description = "Project name used in resource names."
  type        = string
}

variable "tags" {
  description = "Tags applied to VPC resources."
  type        = map(string)
}
