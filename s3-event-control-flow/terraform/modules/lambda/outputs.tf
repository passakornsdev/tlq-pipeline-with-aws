output "dispatcher_lambda_name" {
  value = aws_lambda_function.dispatcher.function_name
}

output "processor_lambda_name" {
  value = aws_lambda_function.processor.function_name
}
