resource "aws_sqs_queue" "dead_letter" {
  name                      = "${var.project_name}-dlq"
  message_retention_seconds = 1209600
  tags                      = var.tags
}

resource "aws_sqs_queue" "batches" {
  name                       = "${var.project_name}-batches"
  max_message_size           = 1048576
  visibility_timeout_seconds = 960
  message_retention_seconds  = 86400
  receive_wait_time_seconds  = 20
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dead_letter.arn
    maxReceiveCount     = 3
  })
  tags = var.tags
}
