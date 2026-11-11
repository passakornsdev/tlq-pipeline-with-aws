variable "source_bucket_name" {
  description = "Name of the CSV source bucket."
  type        = string
}

variable "destination_bucket_name" {
  description = "Name of the JSON destination bucket."
  type        = string
}

variable "tags" {
  description = "Tags applied to S3 resources."
  type        = map(string)
}
