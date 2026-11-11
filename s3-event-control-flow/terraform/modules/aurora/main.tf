locals {
  database_name = "${var.project_name}-aurora"
}

resource "aws_db_subnet_group" "aurora" {
  name       = local.database_name
  subnet_ids = var.subnet_ids
  tags       = var.tags
}

resource "aws_security_group" "aurora" {
  name        = local.database_name
  description = "Allow Aurora MySQL access from the RDS Proxy."
  vpc_id      = var.vpc_id
  tags        = var.tags

  ingress {
    description     = "Aurora MySQL from RDS Proxy."
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.proxy.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_rds_cluster" "aurora" {
  cluster_identifier      = local.database_name
  engine                  = "aurora-mysql"
  database_name           = "tlq"
  master_username         = "admin"
  master_password         = var.password
  db_subnet_group_name    = aws_db_subnet_group.aurora.name
  vpc_security_group_ids  = [aws_security_group.aurora.id]
  storage_encrypted       = true
  backup_retention_period = 7
  skip_final_snapshot     = true
  deletion_protection     = false
  tags                    = var.tags
}

resource "aws_rds_cluster_instance" "aurora_writer" {
  identifier           = "${local.database_name}-writer"
  cluster_identifier   = aws_rds_cluster.aurora.id
  instance_class       = var.instance_class
  engine               = aws_rds_cluster.aurora.engine
  db_subnet_group_name = aws_db_subnet_group.aurora.name
  publicly_accessible  = false
  tags                 = var.tags
}

resource "aws_security_group" "proxy" {
  name        = "${local.database_name}-proxy"
  description = "Allow the loader Lambda to access the RDS Proxy."
  vpc_id      = var.vpc_id
  tags        = var.tags

  ingress {
    description     = "MySQL from Lambda."
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [var.lambda_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_secretsmanager_secret" "aurora" {
  name = "${local.database_name}-credentials"
  tags = var.tags
}

resource "aws_secretsmanager_secret_version" "aurora" {
  secret_id = aws_secretsmanager_secret.aurora.id
  secret_string = jsonencode({
    username = "admin"
    password = var.password
  })
}

resource "aws_iam_role" "proxy" {
  name = "${local.database_name}-proxy-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "rds.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy" "proxy" {
  name = "${local.database_name}-proxy-policy"
  role = aws_iam_role.proxy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = aws_secretsmanager_secret.aurora.arn
      },
      {
        Effect   = "Allow"
        Action   = ["kms:Decrypt"]
        Resource = "*"
      }
    ]
  })
}

resource "aws_db_proxy" "aurora" {
  name                   = "${local.database_name}-proxy"
  engine_family          = "MYSQL"
  require_tls            = true
  idle_client_timeout    = 1800
  vpc_subnet_ids         = var.subnet_ids
  vpc_security_group_ids = [aws_security_group.proxy.id]
  role_arn               = aws_iam_role.proxy.arn
  tags                   = var.tags

  auth {
    auth_scheme = "SECRETS"
    iam_auth    = "DISABLED"
    secret_arn  = aws_secretsmanager_secret.aurora.arn
  }

  depends_on = [
    aws_iam_role_policy.proxy,
    aws_secretsmanager_secret_version.aurora
  ]
}

resource "aws_db_proxy_default_target_group" "aurora" {
  db_proxy_name = aws_db_proxy.aurora.name
}

resource "aws_db_proxy_target" "aurora" {
  db_proxy_name         = aws_db_proxy.aurora.name
  target_group_name     = aws_db_proxy_default_target_group.aurora.name
  db_cluster_identifier = aws_rds_cluster.aurora.id
}
