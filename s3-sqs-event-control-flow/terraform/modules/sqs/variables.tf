variable "project_name" {
  description = "Project name used in queue names."
  type        = string
}

variable "tags" {
  description = "Tags applied to SQS resources."
  type        = map(string)
}
