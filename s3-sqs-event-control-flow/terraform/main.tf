locals {
  deployment_date    = formatdate("YYYYMMDD", time_static.deployment_date.rfc3339)
  source_bucket_name = "${var.source_bucket_name}-${local.deployment_date}"
  common_tags = merge(var.tags, {
    Project = var.project_name
  })
}

resource "time_static" "deployment_date" {}

module "vpc" {
  source = "./modules/vpc"

  aws_region   = var.aws_region
  project_name = var.project_name
  tags         = local.common_tags
}

module "s3" {
  source = "./modules/s3"

  source_bucket_name = local.source_bucket_name
  tags               = local.common_tags
}

module "bastion" {
  source = "./modules/bastion"

  project_name      = var.project_name
  vpc_id            = module.vpc.vpc_id
  subnet_id         = module.vpc.public_subnet_ids[0]
  security_group_id = module.vpc.bastion_security_group_id
  tags              = local.common_tags
}

module "lambda" {
  source = "./modules/lambda"

  project_name             = var.project_name
  source_bucket_name       = module.s3.source_bucket_name
  source_bucket_arn        = module.s3.source_bucket_arn
  sqs_queue_url            = module.sqs.queue_url
  sqs_queue_arn            = module.sqs.queue_arn
  subnet_ids               = module.vpc.public_subnet_ids
  lambda_security_group_id = module.vpc.lambda_security_group_id
  db_host                  = module.aurora.proxy_endpoint
  db_port                  = var.db_port
  db_name                  = var.db_name
  db_secret_arn            = module.aurora.secret_arn
  ses_from_email           = var.ses_from_email
  ses_to_email             = var.ses_to_email
  lambda_filename          = "${path.root}/../target/s3-sqs-event-control-flow-lambda.jar"
  tags                     = local.common_tags
}

module "sqs" {
  source = "./modules/sqs"

  project_name = var.project_name
  tags         = local.common_tags
}

module "aurora" {
  source = "./modules/aurora"

  project_name              = var.project_name
  instance_class            = var.aurora_instance_class
  subnet_ids                = module.vpc.public_subnet_ids
  vpc_id                    = module.vpc.vpc_id
  lambda_security_group_id  = module.vpc.lambda_security_group_id
  bastion_security_group_id = module.vpc.bastion_security_group_id
  tags                      = local.common_tags
}
