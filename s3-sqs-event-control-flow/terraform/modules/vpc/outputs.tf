output "vpc_id" {
  value = aws_vpc.app.id
}

output "primary_subnet_id" {
  value = aws_subnet.public[0].id
}

output "primary_availability_zone" {
  value = aws_subnet.public[0].availability_zone
}

output "database_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "lambda_security_group_id" {
  value = aws_security_group.lambda.id
}

output "bastion_security_group_id" {
  value = aws_security_group.bastion.id
}
