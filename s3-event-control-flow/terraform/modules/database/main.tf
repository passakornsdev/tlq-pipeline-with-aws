locals {
  database_identifier = "${var.project_name}-mysql"
}

resource "aws_db_subnet_group" "database" {
  name       = local.database_identifier
  subnet_ids = var.subnet_ids
  tags       = var.tags
}

resource "aws_security_group" "database" {
  name        = local.database_identifier
  description = "Allow MySQL access from RDS Proxy."
  vpc_id      = var.vpc_id
  tags        = var.tags

  ingress {
    description     = "MySQL from RDS Proxy."
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

resource "aws_security_group" "proxy" {
  name        = "${local.database_identifier}-proxy"
  description = "Allow MySQL access to RDS Proxy from Lambda and the SSM bastion."
  vpc_id      = var.vpc_id
  tags        = var.tags

  ingress {
    description     = "MySQL from Lambda."
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [var.lambda_security_group_id]
  }

  ingress {
    description     = "MySQL from the SSM bastion."
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [var.bastion_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_db_instance" "database" {
  identifier                  = local.database_identifier
  engine                      = "mysql"
  instance_class              = var.instance_class
  allocated_storage           = 20
  storage_type                = "gp3"
  db_name                     = var.database_name
  username                    = "admin"
  manage_master_user_password = true
  availability_zone           = var.availability_zone
  multi_az                    = false
  db_subnet_group_name        = aws_db_subnet_group.database.name
  vpc_security_group_ids      = [aws_security_group.database.id]
  publicly_accessible         = false
  storage_encrypted           = true
  backup_retention_period     = 7
  skip_final_snapshot         = true
  deletion_protection         = false
  tags                        = var.tags
}

resource "aws_iam_role" "proxy" {
  name = "${local.database_identifier}-proxy-role"

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
  name = "${local.database_identifier}-proxy-policy"
  role = aws_iam_role.proxy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = aws_db_instance.database.master_user_secret[0].secret_arn
      },
      {
        Effect   = "Allow"
        Action   = ["kms:Decrypt"]
        Resource = "*"
      }
    ]
  })
}

resource "aws_db_proxy" "database" {
  name                   = "${local.database_identifier}-proxy"
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
    secret_arn  = aws_db_instance.database.master_user_secret[0].secret_arn
  }

  depends_on = [aws_iam_role_policy.proxy]
}

resource "aws_db_proxy_default_target_group" "database" {
  db_proxy_name = aws_db_proxy.database.name
}

resource "aws_db_proxy_target" "database" {
  db_proxy_name          = aws_db_proxy.database.name
  target_group_name      = aws_db_proxy_default_target_group.database.name
  db_instance_identifier = aws_db_instance.database.identifier
}
