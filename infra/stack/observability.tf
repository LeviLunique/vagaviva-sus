# Alarmes (sintomas percebidos pelo usuário primeiro) e painel operacional.

resource "aws_sns_topic" "alarms" {
  name              = "${local.name}-alarms"
  kms_master_key_id = "alias/aws/sns"
}

resource "aws_sns_topic_subscription" "alarms_email" {
  count     = var.alarm_email == "" ? 0 : 1
  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

locals {
  alarm_actions = [aws_sns_topic.alarms.arn]
  alb_dims      = { LoadBalancer = aws_lb.api.arn_suffix }
  tg_dims       = { LoadBalancer = aws_lb.api.arn_suffix, TargetGroup = aws_lb_target_group.api.arn_suffix }
  ecs_dims      = { ClusterName = aws_ecs_cluster.this.name, ServiceName = aws_ecs_service.api.name }
  rds_dims      = { DBInstanceIdentifier = aws_db_instance.this.identifier }
}

resource "aws_cloudwatch_metric_alarm" "api_5xx" {
  alarm_name          = "${local.name}-api-5xx"
  alarm_description   = "Erros 5xx da API acima do normal"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HTTPCode_Target_5XX_Count"
  dimensions          = local.alb_dims
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 10
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "api_latency_p95" {
  alarm_name          = "${local.name}-api-latency-p95"
  alarm_description   = "Latência p95 da API acima de 1s (SLO)"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "TargetResponseTime"
  dimensions          = local.alb_dims
  extended_statistic  = "p95"
  period              = 300
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "api_unhealthy" {
  alarm_name          = "${local.name}-api-unhealthy-targets"
  alarm_description   = "Tasks da API falhando no health check do ALB"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  dimensions          = local.tg_dims
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 5
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "ecs_cpu" {
  alarm_name          = "${local.name}-api-cpu"
  alarm_description   = "CPU média das tasks acima de 85% (autoscaling no limite?)"
  namespace           = "AWS/ECS"
  metric_name         = "CPUUtilization"
  dimensions          = local.ecs_dims
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 85
  comparison_operator = "GreaterThanThreshold"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "ecs_memory" {
  alarm_name          = "${local.name}-api-memory"
  alarm_description   = "Memória média das tasks acima de 85%"
  namespace           = "AWS/ECS"
  metric_name         = "MemoryUtilization"
  dimensions          = local.ecs_dims
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 85
  comparison_operator = "GreaterThanThreshold"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "rds_cpu" {
  alarm_name          = "${local.name}-db-cpu"
  alarm_description   = "CPU do PostgreSQL acima de 80%"
  namespace           = "AWS/RDS"
  metric_name         = "CPUUtilization"
  dimensions          = local.rds_dims
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "rds_storage" {
  alarm_name          = "${local.name}-db-free-storage"
  alarm_description   = "Menos de 2 GiB livres no PostgreSQL"
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  dimensions          = local.rds_dims
  statistic           = "Minimum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 2147483648
  comparison_operator = "LessThanThreshold"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "notifications_dlq" {
  alarm_name          = "${local.name}-notifications-dlq"
  alarm_description   = "Notificações a pacientes falhando repetidamente (mensagens na DLQ)"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  dimensions          = { QueueName = aws_sqs_queue.notifications_dlq.name }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "notifications_backlog" {
  alarm_name          = "${local.name}-notifications-backlog"
  alarm_description   = "Notificações aguardando mais de 10 minutos na fila"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateAgeOfOldestMessage"
  dimensions          = { QueueName = aws_sqs_queue.notifications.name }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 2
  threshold           = 600
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

# ---------------------------------------------------------------- negócio (F7, namespace VagaViva via ADOT)

resource "aws_cloudwatch_metric_alarm" "notifications_failed" {
  alarm_name          = "${local.name}-notifications-failed"
  alarm_description   = "Tentativas de envio de mensagem ao paciente falhando (provedor de SMS/WhatsApp)"
  namespace           = "VagaViva"
  metric_name         = "vagaviva.notifications"
  dimensions          = { status = "FAILED" }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 5
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

# Nenhum agendamento criado por 2 h seguidas em horário comercial (seg-sex, 8h-18h em São Paulo = 11h-21h UTC).
resource "aws_cloudwatch_metric_alarm" "allocation_stalled" {
  alarm_name          = "${local.name}-allocation-stalled"
  alarm_description   = "Motor de alocação sem criar agendamentos em horário comercial"
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  metric_query {
    id = "scheduled"
    metric {
      namespace   = "VagaViva"
      metric_name = "vagaviva.appointments.scheduled"
      stat        = "Sum"
      period      = 3600
    }
  }
  metric_query {
    id         = "filled"
    expression = "FILL(scheduled, 0)"
  }
  metric_query {
    id          = "business_hours"
    expression  = "IF(HOUR(filled) >= 11 AND HOUR(filled) <= 20 AND DAY(filled) <= 5, filled, 1)"
    label       = "Agendamentos criados (fora do horário comercial conta como 1)"
    return_data = true
  }
}

resource "aws_cloudwatch_dashboard" "ops" {
  dashboard_name = "${local.name}-operacao"
  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric", x = 0, y = 0, width = 12, height = 6
        properties = {
          title  = "API - requisições e erros"
          region = local.region
          stat   = "Sum"
          period = 60
          metrics = [
            ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", aws_lb.api.arn_suffix],
            [".", "HTTPCode_Target_4XX_Count", ".", "."],
            [".", "HTTPCode_Target_5XX_Count", ".", "."],
          ]
        }
      },
      {
        type = "metric", x = 12, y = 0, width = 12, height = 6
        properties = {
          title  = "API - latência (p50/p95/p99)"
          region = local.region
          period = 60
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", aws_lb.api.arn_suffix, { stat = "p50" }],
            ["...", { stat = "p95" }],
            ["...", { stat = "p99" }],
          ]
        }
      },
      {
        type = "metric", x = 0, y = 6, width = 12, height = 6
        properties = {
          title  = "ECS - CPU / memória / tasks"
          region = local.region
          stat   = "Average"
          period = 60
          metrics = [
            ["AWS/ECS", "CPUUtilization", "ClusterName", aws_ecs_cluster.this.name, "ServiceName", aws_ecs_service.api.name],
            [".", "MemoryUtilization", ".", ".", ".", "."],
            ["ECS/ContainerInsights", "RunningTaskCount", ".", ".", ".", ".", { stat = "Maximum", yAxis = "right" }],
          ]
        }
      },
      {
        type = "metric", x = 12, y = 6, width = 12, height = 6
        properties = {
          title  = "PostgreSQL - CPU / conexões"
          region = local.region
          stat   = "Average"
          period = 60
          metrics = [
            ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", aws_db_instance.this.identifier],
            [".", "DatabaseConnections", ".", ".", { yAxis = "right" }],
          ]
        }
      },
      {
        type = "metric", x = 0, y = 12, width = 24, height = 6
        properties = {
          title  = "Notificações - fila e DLQ"
          region = local.region
          stat   = "Maximum"
          period = 60
          metrics = [
            ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", aws_sqs_queue.notifications.name],
            [".", "ApproximateAgeOfOldestMessage", ".", ".", { yAxis = "right" }],
            [".", "ApproximateNumberOfMessagesVisible", ".", aws_sqs_queue.notifications_dlq.name],
          ]
        }
      },
      {
        type = "metric", x = 0, y = 18, width = 12, height = 6
        properties = {
          title  = "Negócio - agendamentos por origem e desfechos"
          region = local.region
          stat   = "Sum"
          period = 300
          metrics = [
            [{ expression = "SEARCH('{VagaViva,origin} MetricName=\"vagaviva.appointments.scheduled\"', 'Sum', 300)", id = "scheduled" }],
            [{ expression = "SEARCH('{VagaViva,status} MetricName=\"vagaviva.appointments.outcome\"', 'Sum', 300)", id = "outcome" }],
          ]
        }
      },
      {
        type = "metric", x = 12, y = 18, width = 12, height = 6
        properties = {
          title  = "Negócio - vagas liberadas, reaproveitadas e perdidas"
          region = local.region
          stat   = "Sum"
          period = 300
          metrics = [
            [{ expression = "SEARCH('{VagaViva,reason} MetricName=\"vagaviva.slots.released\"', 'Sum', 300)", id = "released" }],
            [{ expression = "SEARCH('{VagaViva,via} MetricName=\"vagaviva.slots.reallocated\"', 'Sum', 300)", id = "reallocated" }],
            ["VagaViva", "vagaviva.slots.lost", { id = "lost" }],
          ]
        }
      },
      {
        type = "metric", x = 0, y = 24, width = 12, height = 6
        properties = {
          title  = "Negócio - mensagens e ofertas de encaixe"
          region = local.region
          stat   = "Sum"
          period = 300
          metrics = [
            [{ expression = "SEARCH('{VagaViva,status} MetricName=\"vagaviva.notifications\"', 'Sum', 300)", id = "notifications" }],
            [{ expression = "SEARCH('{VagaViva,status} MetricName=\"vagaviva.offers\"', 'Sum', 300)", id = "offers" }],
          ]
        }
      },
      {
        type = "metric", x = 12, y = 24, width = 12, height = 6
        properties = {
          title  = "Negócio - fila por especialidade e duração da alocação"
          region = local.region
          period = 300
          metrics = [
            [{ expression = "SEARCH('{VagaViva,specialty} MetricName=\"vagaviva.queue.waiting\"', 'Maximum', 300)", id = "queue" }],
            ["VagaViva", "vagaviva.allocation.duration", { stat = "Maximum", yAxis = "right", id = "allocation" }],
          ]
        }
      },
    ]
  })
}
