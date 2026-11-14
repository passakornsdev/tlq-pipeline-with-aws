output "instance_id" {
  description = "SSM target instance ID for port forwarding."
  value       = aws_instance.bastion.id
}
