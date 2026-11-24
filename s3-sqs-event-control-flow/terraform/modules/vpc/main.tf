locals {
  vpc_cidr     = "10.0.0.0/16"
  subnet_cidrs = ["10.0.1.0/24", "10.0.2.0/24"]
}

data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "app" {
  cidr_block           = local.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true
  tags                 = var.tags
}

resource "aws_subnet" "workload" {
  count                   = length(local.subnet_cidrs)
  vpc_id                  = aws_vpc.app.id
  cidr_block              = local.subnet_cidrs[count.index]
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = false
  tags                    = merge(var.tags, { Tier = "private" })
}

resource "aws_route_table" "workload" {
  vpc_id = aws_vpc.app.id
  tags   = var.tags
}

resource "aws_route_table_association" "workload" {
  count          = length(aws_subnet.workload)
  subnet_id      = aws_subnet.workload[count.index].id
  route_table_id = aws_route_table.workload.id
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.app.id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.workload.id]
  tags              = var.tags
}

resource "aws_vpc_endpoint" "sqs" {
  vpc_id              = aws_vpc.app.id
  service_name        = "com.amazonaws.${var.aws_region}.sqs"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = [aws_subnet.workload[0].id]
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true
  tags                = var.tags
}

resource "aws_security_group" "lambda" {
  name        = "${var.project_name}-lambda"
  description = "Allow outbound access for VPC-connected Lambda functions."
  vpc_id      = aws_vpc.app.id
  tags        = var.tags

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "bastion" {
  name        = "${var.project_name}-bastion"
  description = "Outbound-only access for the SSM bastion host."
  vpc_id      = aws_vpc.app.id
  tags        = var.tags

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "vpc_endpoints" {
  name        = "${var.project_name}-vpc-endpoints"
  description = "Allow Lambda and bastion access to VPC interface endpoints."
  vpc_id      = aws_vpc.app.id
  tags        = var.tags

  ingress {
    description     = "HTTPS from Lambda and the SSM bastion."
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [aws_security_group.lambda.id, aws_security_group.bastion.id]
  }
}

resource "aws_vpc_endpoint" "lambda" {
  vpc_id              = aws_vpc.app.id
  service_name        = "com.amazonaws.${var.aws_region}.lambda"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = [aws_subnet.workload[0].id]
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true
  tags                = var.tags
}

resource "aws_vpc_endpoint" "logs" {
  vpc_id              = aws_vpc.app.id
  service_name        = "com.amazonaws.${var.aws_region}.logs"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = [aws_subnet.workload[0].id]
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true
  tags                = var.tags
}

resource "aws_vpc_endpoint" "secretsmanager" {
  vpc_id              = aws_vpc.app.id
  service_name        = "com.amazonaws.${var.aws_region}.secretsmanager"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = [aws_subnet.workload[0].id]
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true
  tags                = var.tags
}

resource "aws_vpc_endpoint" "ses" {
  vpc_id              = aws_vpc.app.id
  service_name        = "com.amazonaws.${var.aws_region}.email"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = [aws_subnet.workload[0].id]
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true
  tags                = var.tags
}

resource "aws_vpc_endpoint" "ssm" {
  vpc_id              = aws_vpc.app.id
  service_name        = "com.amazonaws.${var.aws_region}.ssm"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = [aws_subnet.workload[0].id]
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true
  tags                = var.tags
}

resource "aws_vpc_endpoint" "ssmmessages" {
  vpc_id              = aws_vpc.app.id
  service_name        = "com.amazonaws.${var.aws_region}.ssmmessages"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = [aws_subnet.workload[0].id]
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true
  tags                = var.tags
}

resource "aws_vpc_endpoint" "ec2messages" {
  vpc_id              = aws_vpc.app.id
  service_name        = "com.amazonaws.${var.aws_region}.ec2messages"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = [aws_subnet.workload[0].id]
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true
  tags                = var.tags
}

moved {
  from = aws_subnet.public[0]
  to   = aws_subnet.workload[0]
}

moved {
  from = aws_subnet.public[1]
  to   = aws_subnet.workload[1]
}

moved {
  from = aws_route_table.public
  to   = aws_route_table.workload
}

moved {
  from = aws_route_table_association.public[0]
  to   = aws_route_table_association.workload[0]
}

moved {
  from = aws_route_table_association.public[1]
  to   = aws_route_table_association.workload[1]
}
