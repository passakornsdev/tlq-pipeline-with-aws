output "endpoint" {
  value = aws_db_instance.database.address
}

output "proxy_endpoint" {
  value = aws_db_proxy.database.endpoint
}

output "secret_arn" {
  value = aws_db_instance.database.master_user_secret[0].secret_arn
}
