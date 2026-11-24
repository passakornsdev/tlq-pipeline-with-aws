variable "project_name" {
  description = "Project name used in resource names."
  type        = string
}

variable "instance_class" {
  description = "MySQL RDS instance class."
  type        = string
}

variable "database_name" {
  description = "MySQL database name."
  type        = string
}

variable "availability_zone" {
  description = "Availability Zone where the single-AZ database instance runs."
  type        = string
}

variable "subnet_ids" {
  description = "Subnets spanning two AZs, as required by the RDS DB subnet group."
  type        = list(string)
}

variable "vpc_id" {
  description = "VPC containing the database."
  type        = string
}

variable "lambda_security_group_id" {
  description = "Security group allowed to access the database."
  type        = string
}

variable "bastion_security_group_id" {
  description = "Security group allowed to access the database for administration."
  type        = string
}

variable "tags" {
  description = "Tags applied to database resources."
  type        = map(string)
}
