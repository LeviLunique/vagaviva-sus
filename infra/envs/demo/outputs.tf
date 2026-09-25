output "api_base_url" {
  description = "URL publica (HTTPS) do perfil demo."
  value       = "https://${aws_cloudfront_distribution.demo.domain_name}"
}

output "cloudfront_distribution_id" {
  value = aws_cloudfront_distribution.demo.id
}

output "instance_id" {
  value = aws_instance.app.id
}

output "image_tag_parameter" {
  value = aws_ssm_parameter.image_tag.name
}

output "app_secret_arn" {
  value = aws_secretsmanager_secret.app.arn
}

output "db_secret_arn" {
  value = aws_secretsmanager_secret.db.arn
}

output "notifications_queue_url" {
  value = aws_sqs_queue.notifications.url
}

output "log_group" {
  value = aws_cloudwatch_log_group.instance.name
}

output "wake_lambda_function_name" {
  value = aws_lambda_function.wake.function_name
}

output "idle_shutdown_lambda_function_name" {
  value = aws_lambda_function.idle_shutdown.function_name
}
