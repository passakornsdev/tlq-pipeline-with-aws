output "endpoint" {
  value = aws_rds_cluster.aurora.endpoint
}

output "proxy_endpoint" {
  value = aws_db_proxy.aurora.endpoint
}

output "secret_arn" {
  value = aws_rds_cluster.aurora.master_user_secret[0].secret_arn
}
