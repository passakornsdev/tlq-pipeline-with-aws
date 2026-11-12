output "vpc_id" {
  value = aws_vpc.app.id
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "lambda_security_group_id" {
  value = aws_security_group.lambda.id
}

output "bastion_security_group_id" {
  value = aws_security_group.bastion.id
}
