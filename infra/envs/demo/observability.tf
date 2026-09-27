# Observabilidade do demo (F7): painel com a instância, a fila de mensagens e os indicadores de
# negócio (namespace VagaViva, enviados pelo coletor ADOT) e alarmes de mensagens.
# Sem o alarme de "alocação parada" do desenho de produção: o demo fica desligado a maior parte do
# tempo e não há publicação de agenda contínua — o alarme dispararia sem sentido.

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
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
}

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
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
}

resource "aws_cloudwatch_dashboard" "demo" {
  dashboard_name = "${local.name}-operacao"
  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric", x = 0, y = 0, width = 12, height = 6
        properties = {
          title  = "Instância - CPU e rede"
          region = local.region
          period = 60
          metrics = [
            ["AWS/EC2", "CPUUtilization", "InstanceId", aws_instance.app.id, { stat = "Average" }],
            [".", "NetworkIn", ".", ".", { stat = "Sum", yAxis = "right" }],
          ]
        }
      },
      {
        type = "metric", x = 12, y = 0, width = 12, height = 6
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
        type = "metric", x = 0, y = 6, width = 12, height = 6
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
        type = "metric", x = 12, y = 6, width = 12, height = 6
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
        type = "metric", x = 0, y = 12, width = 12, height = 6
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
        type = "metric", x = 12, y = 12, width = 12, height = 6
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
