output "endpoint" {
  value = aws_rds_cluster.aurora.endpoint
}

output "proxy_endpoint" {
  value = aws_db_proxy.aurora.endpoint
}
