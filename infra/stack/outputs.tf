output "api_base_url" {
  description = "URL pública (HTTPS) da API."
  value       = "https://${aws_cloudfront_distribution.api.domain_name}"
}

output "cloudfront_distribution_id" {
  value = aws_cloudfront_distribution.api.id
}

output "alb_dns_name" {
  value = aws_lb.api.dns_name
}

output "ecs_cluster" {
  value = aws_ecs_cluster.this.name
}

output "ecs_service" {
  value = aws_ecs_service.api.name
}

output "db_endpoint" {
  value = aws_db_instance.this.address
}

output "db_secret_arn" {
  value = aws_secretsmanager_secret.db.arn
}

output "app_secret_arn" {
  value = aws_secretsmanager_secret.app.arn
}

output "notifications_queue_url" {
  value = aws_sqs_queue.notifications.url
}

output "notifications_dlq_url" {
  value = aws_sqs_queue.notifications_dlq.url
}

output "image_tag_parameter" {
  value = aws_ssm_parameter.image_tag.name
}

output "log_group" {
  value = aws_cloudwatch_log_group.api.name
}

output "alarms_topic_arn" {
  value = aws_sns_topic.alarms.arn
}

output "dashboard_url" {
  value = "https://${local.region}.console.aws.amazon.com/cloudwatch/home?region=${local.region}#dashboards:name=${aws_cloudwatch_dashboard.ops.dashboard_name}"
}
