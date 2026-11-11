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
  description = "Allow Aurora MySQL access from the VPC Lambda functions."
  vpc_id      = var.vpc_id
  tags        = var.tags

  ingress {
    description     = "Aurora MySQL from Lambda."
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
