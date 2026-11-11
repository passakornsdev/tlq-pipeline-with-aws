locals {
  dispatcher_name = "${var.project_name}-dispatcher"
  processor_name  = "${var.project_name}-processor"
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

  tags = var.tags
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

  tags = var.tags
}

resource "aws_cloudwatch_log_group" "dispatcher" {
  name              = "/aws/lambda/${local.dispatcher_name}"
  retention_in_days = 14
  tags              = var.tags
}

resource "aws_cloudwatch_log_group" "processor" {
  name              = "/aws/lambda/${local.processor_name}"
  retention_in_days = 14
  tags              = var.tags
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
        Resource = "${var.source_bucket_arn}/*"
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
        Resource = "${var.source_bucket_arn}/*"
      },
      {
        Effect   = "Allow"
        Action   = "s3:PutObject"
        Resource = "${var.destination_bucket_arn}/*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "dispatcher_vpc_access" {
  role       = aws_iam_role.dispatcher.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

resource "aws_iam_role_policy_attachment" "processor_vpc_access" {
  role       = aws_iam_role.processor.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

resource "aws_lambda_function" "processor" {
  function_name    = local.processor_name
  role             = aws_iam_role.processor.arn
  runtime          = "java21"
  handler          = "com.passakornsdev.tlq.csv.transfer.ProcessorHandler::handleRequest"
  filename         = var.lambda_filename
  source_code_hash = filebase64sha256(var.lambda_filename)
  memory_size      = 1024
  timeout          = 900
  tags             = var.tags

  vpc_config {
    subnet_ids         = var.subnet_ids
    security_group_ids = [var.lambda_security_group_id]
  }

  depends_on = [
    aws_cloudwatch_log_group.processor,
    aws_iam_role_policy.processor,
    aws_iam_role_policy_attachment.processor_vpc_access
  ]
}

resource "aws_lambda_function" "dispatcher" {
  function_name    = local.dispatcher_name
  role             = aws_iam_role.dispatcher.arn
  runtime          = "java21"
  handler          = "com.passakornsdev.tlq.csv.transfer.DispatcherHandler::handleRequest"
  filename         = var.lambda_filename
  source_code_hash = filebase64sha256(var.lambda_filename)
  memory_size      = 1024
  timeout          = 900
  tags             = var.tags

  vpc_config {
    subnet_ids         = var.subnet_ids
    security_group_ids = [var.lambda_security_group_id]
  }

  environment {
    variables = {
      DESTINATION_BUCKET      = var.destination_bucket_name
      PROCESSOR_FUNCTION_NAME = aws_lambda_function.processor.function_name
    }
  }

  depends_on = [
    aws_cloudwatch_log_group.dispatcher,
    aws_iam_role_policy.dispatcher,
    aws_iam_role_policy_attachment.dispatcher_vpc_access
  ]
}

resource "aws_lambda_permission" "source_invokes_dispatcher" {
  statement_id  = "AllowSourceBucketInvocation"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.dispatcher.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = var.source_bucket_arn
}

resource "aws_s3_bucket_notification" "source" {
  bucket = var.source_bucket_name

  lambda_function {
    lambda_function_arn = aws_lambda_function.dispatcher.arn
    events              = ["s3:ObjectCreated:*"]
    filter_suffix       = ".csv"
  }

  depends_on = [aws_lambda_permission.source_invokes_dispatcher]
}
