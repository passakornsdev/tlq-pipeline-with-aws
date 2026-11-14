locals {
  dispatcher_name = "${var.project_name}-dispatcher"
  processor_name  = "${var.project_name}-processor"
  query_name      = "${var.project_name}-query"
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

resource "aws_iam_role" "loader" {
  name = "${var.project_name}-loader-role"

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

resource "aws_iam_role" "query" {
  name = "${local.query_name}-role"

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

resource "aws_cloudwatch_log_group" "loader" {
  name              = "/aws/lambda/${var.project_name}-loader"
  retention_in_days = 14
  tags              = var.tags
}

resource "aws_cloudwatch_log_group" "query" {
  name              = "/aws/lambda/${local.query_name}"
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
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = var.db_secret_arn
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
        Action   = ["sqs:SendMessage"]
        Resource = var.sqs_queue_arn
      }
    ]
  })
}

resource "aws_iam_role_policy" "loader" {
  name = "${var.project_name}-loader-policy"
  role = aws_iam_role.loader.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "${aws_cloudwatch_log_group.loader.arn}:*"
      },
      {
        Effect = "Allow"
        Action = [
          "sqs:ChangeMessageVisibility",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes",
          "sqs:ReceiveMessage"
        ]
        Resource = var.sqs_queue_arn
      },
      {
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = var.db_secret_arn
      },
      {
        Effect   = "Allow"
        Action   = "lambda:InvokeFunction"
        Resource = aws_lambda_function.query.arn
      }
    ]
  })
}

resource "aws_iam_role_policy" "query" {
  name = "${local.query_name}-policy"
  role = aws_iam_role.query.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "${aws_cloudwatch_log_group.query.arn}:*"
      },
      {
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = var.db_secret_arn
      },
      {
        Effect   = "Allow"
        Action   = ["ses:SendRawEmail"]
        Resource = "*"
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

resource "aws_iam_role_policy_attachment" "loader_vpc_access" {
  role       = aws_iam_role.loader.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

resource "aws_iam_role_policy_attachment" "query_vpc_access" {
  role       = aws_iam_role.query.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

resource "aws_lambda_function" "processor" {
  function_name    = local.processor_name
  role             = aws_iam_role.processor.arn
  runtime          = "java25"
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

  environment {
    variables = {
      SQS_QUEUE_URL = var.sqs_queue_url
    }
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
  runtime          = "java25"
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
      PROCESSOR_FUNCTION_NAME = aws_lambda_function.processor.function_name
      DB_HOST                 = var.db_host
      DB_PORT                 = tostring(var.db_port)
      DB_NAME                 = var.db_name
      DB_SECRET_ARN           = var.db_secret_arn
    }
  }

  depends_on = [
    aws_cloudwatch_log_group.dispatcher,
    aws_iam_role_policy.dispatcher,
    aws_iam_role_policy_attachment.dispatcher_vpc_access
  ]
}

resource "aws_lambda_function" "loader" {
  function_name    = "${var.project_name}-loader"
  role             = aws_iam_role.loader.arn
  runtime          = "java25"
  handler          = "com.passakornsdev.tlq.csv.load.LoadHandler::handleRequest"
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
      DB_HOST             = var.db_host
      DB_PORT             = tostring(var.db_port)
      DB_NAME             = var.db_name
      DB_SECRET_ARN       = var.db_secret_arn
      QUERY_FUNCTION_NAME = aws_lambda_function.query.function_name
    }
  }

  depends_on = [
    aws_cloudwatch_log_group.loader,
    aws_iam_role_policy.loader,
    aws_iam_role_policy_attachment.loader_vpc_access
  ]
}

resource "aws_lambda_function" "query" {
  function_name    = local.query_name
  role             = aws_iam_role.query.arn
  runtime          = "java25"
  handler          = "com.passakornsdev.tlq.csv.query.PaymentQueryHandler::handleRequest"
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
      DB_HOST        = var.db_host
      DB_PORT        = tostring(var.db_port)
      DB_NAME        = var.db_name
      DB_SECRET_ARN  = var.db_secret_arn
      SES_FROM_EMAIL = var.ses_from_email
      SES_TO_EMAIL   = var.ses_to_email
    }
  }

  depends_on = [
    aws_cloudwatch_log_group.query,
    aws_iam_role_policy.query,
    aws_iam_role_policy_attachment.query_vpc_access
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

resource "aws_lambda_event_source_mapping" "loader" {
  event_source_arn                   = var.sqs_queue_arn
  function_name                      = aws_lambda_function.loader.arn
  batch_size                         = 1
  maximum_batching_window_in_seconds = 0
  enabled                            = true
}
