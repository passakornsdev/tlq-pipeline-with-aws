locals {
  deployment_date         = formatdate("YYYYMMDD", time_static.deployment_date.rfc3339)
  source_bucket_name      = "${var.source_bucket_name}-${local.deployment_date}"
  destination_bucket_name = "${var.destination_bucket_name}-${local.deployment_date}"
  dispatcher_name         = "${var.project_name}-dispatcher"
  processor_name          = "${var.project_name}-processor"
  database_name           = "${var.project_name}-aurora"
  common_tags = merge(var.tags, {
    Project = var.project_name
  })
}

resource "time_static" "deployment_date" {}

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default_vpc" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

resource "aws_s3_bucket" "source" {
  bucket = local.source_bucket_name
  tags   = local.common_tags
}

resource "aws_s3_bucket" "destination" {
  bucket = local.destination_bucket_name
  tags   = local.common_tags
}

resource "aws_s3_bucket_versioning" "source" {
  bucket = aws_s3_bucket.source.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_versioning" "destination" {
  bucket = aws_s3_bucket.destination.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "source" {
  bucket                  = aws_s3_bucket.source.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_public_access_block" "destination" {
  bucket                  = aws_s3_bucket.destination.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "source" {
  bucket = aws_s3_bucket.source.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "destination" {
  bucket = aws_s3_bucket.destination.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_ownership_controls" "source" {
  bucket = aws_s3_bucket.source.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_ownership_controls" "destination" {
  bucket = aws_s3_bucket.destination.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_iam_role" "dispatcher" {
  name = "${local.dispatcher_name}-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })

  tags = local.common_tags
}

resource "aws_iam_role" "processor" {
  name = "${local.processor_name}-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })

  tags = local.common_tags
}

resource "aws_cloudwatch_log_group" "dispatcher" {
  name              = "/aws/lambda/${local.dispatcher_name}"
  retention_in_days = 14
  tags              = local.common_tags
}

resource "aws_cloudwatch_log_group" "processor" {
  name              = "/aws/lambda/${local.processor_name}"
  retention_in_days = 14
  tags              = local.common_tags
}

resource "aws_iam_role_policy" "dispatcher" {
  name = "${local.dispatcher_name}-policy"
  role = aws_iam_role.dispatcher.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "${aws_cloudwatch_log_group.dispatcher.arn}:*"
      },
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:GetObjectVersion"]
        Resource = "${aws_s3_bucket.source.arn}/*"
      },
      {
        Effect   = "Allow"
        Action   = "lambda:InvokeFunction"
        Resource = aws_lambda_function.processor.arn
      }
    ]
  })
}

resource "aws_iam_role_policy" "processor" {
  name = "${local.processor_name}-policy"
  role = aws_iam_role.processor.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "${aws_cloudwatch_log_group.processor.arn}:*"
      },
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:GetObjectVersion"]
        Resource = "${aws_s3_bucket.source.arn}/*"
      },
      {
        Effect   = "Allow"
        Action   = "s3:PutObject"
        Resource = "${aws_s3_bucket.destination.arn}/*"
      }
    ]
  })
}

resource "aws_lambda_function" "processor" {
  function_name = local.processor_name
  role          = aws_iam_role.processor.arn
  runtime       = "java21"
  handler       = "com.passakornsdev.tlq.csv.transfer.ProcessorHandler::handleRequest"
  filename      = "${path.module}/../target/s3-event-control-flow-lambda.jar"
  source_code_hash = filebase64sha256(
    "${path.module}/../target/s3-event-control-flow-lambda.jar"
  )
  memory_size = 1024
  timeout     = 900
  tags        = local.common_tags

  depends_on = [
    aws_cloudwatch_log_group.processor,
    aws_iam_role_policy.processor
  ]
}

resource "aws_lambda_function" "dispatcher" {
  function_name = local.dispatcher_name
  role          = aws_iam_role.dispatcher.arn
  runtime       = "java21"
  handler       = "com.passakornsdev.tlq.csv.transfer.DispatcherHandler::handleRequest"
  filename      = "${path.module}/../target/s3-event-control-flow-lambda.jar"
  source_code_hash = filebase64sha256(
    "${path.module}/../target/s3-event-control-flow-lambda.jar"
  )
  memory_size = 1024
  timeout     = 900
  tags        = local.common_tags

  environment {
    variables = {
      DESTINATION_BUCKET      = aws_s3_bucket.destination.bucket
      PROCESSOR_FUNCTION_NAME = aws_lambda_function.processor.function_name
    }
  }

  depends_on = [
    aws_cloudwatch_log_group.dispatcher,
    aws_iam_role_policy.dispatcher
  ]
}

resource "aws_lambda_permission" "source_invokes_dispatcher" {
  statement_id  = "AllowSourceBucketInvocation"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.dispatcher.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.source.arn
}

resource "aws_s3_bucket_notification" "source" {
  bucket = aws_s3_bucket.source.id

  lambda_function {
    lambda_function_arn = aws_lambda_function.dispatcher.arn
    events              = ["s3:ObjectCreated:*"]
    filter_suffix       = ".csv"
  }

  depends_on = [aws_lambda_permission.source_invokes_dispatcher]
}

resource "aws_db_subnet_group" "aurora" {
  name       = local.database_name
  subnet_ids = data.aws_subnets.default_vpc.ids
  tags       = local.common_tags
}

resource "aws_security_group" "aurora" {
  name        = local.database_name
  description = "Allow Aurora MySQL access from the default VPC."
  vpc_id      = data.aws_vpc.default.id
  tags        = local.common_tags

  ingress {
    description = "Aurora MySQL from the default VPC."
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    cidr_blocks = [data.aws_vpc.default.cidr_block]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_rds_cluster" "aurora" {
  cluster_identifier        = local.database_name
  engine                    = "aurora-mysql"
  database_name             = "tlq"
  master_username           = "admin"
  manage_master_user_password = true
  db_subnet_group_name      = aws_db_subnet_group.aurora.name
  vpc_security_group_ids   = [aws_security_group.aurora.id]
  storage_encrypted         = true
  backup_retention_period   = 7
  skip_final_snapshot       = true
  deletion_protection       = false
  tags                      = local.common_tags
}

resource "aws_rds_cluster_instance" "aurora_writer" {
  identifier         = "${local.database_name}-writer"
  cluster_identifier  = aws_rds_cluster.aurora.id
  instance_class      = var.aurora_instance_class
  engine              = aws_rds_cluster.aurora.engine
  db_subnet_group_name = aws_db_subnet_group.aurora.name
  publicly_accessible = false
  tags                = local.common_tags
}
