variable "project_name" {
  description = "Project name used in resource names."
  type        = string
}

variable "vpc_id" {
  description = "VPC containing the bastion host."
  type        = string
}

variable "subnet_id" {
  description = "Public subnet for the bastion host."
  type        = string
}

variable "security_group_id" {
  description = "Security group attached to the bastion host."
  type        = string
}

variable "tags" {
  description = "Tags applied to bastion resources."
  type        = map(string)
}
