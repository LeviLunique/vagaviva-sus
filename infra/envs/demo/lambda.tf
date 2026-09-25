# Duas funcoes pequenas que automatizam o liga/desliga da instancia unica do perfil demo.

data "archive_file" "wake" {
  type        = "zip"
  source_dir  = "${path.module}/lambda/wake"
  output_path = "${path.module}/.build/wake.zip"
}

data "archive_file" "idle_shutdown" {
  type        = "zip"
  source_dir  = "${path.module}/lambda/idle-shutdown"
  output_path = "${path.module}/.build/idle-shutdown.zip"
}

data "aws_iam_policy_document" "lambda_trust" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

# ---------------------------------------------------------------- acordar (origem secundaria)
#
# A combinação "Origin Access Control (SigV4) + Lambda Function URL como membro de um origin
# group de failover" se mostrou não confiável na prática (a CloudFront devolvia 403 da própria
# Lambda mesmo com a permissão e o OAC corretos - possível limitação não documentada dessa
# combinação específica). Em vez disso, a URL da função é pública (`authorization_type = NONE`),
# protegida por um segredo compartilhado gerado pelo Terraform (nunca aparece no código-fonte,
# só no estado remoto e nas variáveis de ambiente): a CloudFront injeta esse valor num cabeçalho
# customizado só na origem "wake", e a função só liga a instância se o cabeçalho bater. Pior caso
# de alguém descobrir a URL pública da função e chamá-la sem o segredo: nada acontece (a função
# só responde a página, sem ligar a instância). Aceitável para o perfil demo (ver ADR-0012).

resource "random_password" "wake_shared_secret" {
  length  = 32
  special = false
}

resource "aws_iam_role" "wake" {
  name               = "${local.name}-wake"
  assume_role_policy = data.aws_iam_policy_document.lambda_trust.json
}

resource "aws_iam_role_policy" "wake" {
  name = "start-instance"
  role = aws_iam_role.wake.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # DescribeInstances não suporta permissão em nível de recurso (é uma consulta, não uma
        # ação sobre UM recurso) — condição por tag nela é ignorada silenciosamente (implicit
        # deny). Já StartInstances aceita ARN exato, que é mais preciso que condição por tag.
        Effect   = "Allow"
        Action   = "ec2:DescribeInstances"
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = "ec2:StartInstances"
        Resource = aws_instance.app.arn
      },
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "arn:${local.partition}:logs:${local.region}:${local.account_id}:log-group:/aws/lambda/${local.name}-wake:*"
      }
    ]
  })
}

resource "aws_lambda_function" "wake" {
  function_name    = "${local.name}-wake"
  role             = aws_iam_role.wake.arn
  handler          = "handler.handler"
  runtime          = "python3.13"
  architectures    = ["arm64"]
  timeout          = 10
  memory_size      = 128
  filename         = data.archive_file.wake.output_path
  source_code_hash = data.archive_file.wake.output_base64sha256

  environment {
    variables = {
      INSTANCE_ID                = aws_instance.app.id
      SHARED_SECRET_HEADER_VALUE = random_password.wake_shared_secret.result
    }
  }
}

resource "aws_lambda_function_url" "wake" {
  function_name      = aws_lambda_function.wake.function_name
  authorization_type = "NONE" # protegida pelo segredo compartilhado (ver comentário acima)
}

resource "aws_lambda_permission" "wake_public_url" {
  statement_id           = "AllowPublicFunctionUrl"
  action                 = "lambda:InvokeFunctionUrl"
  function_name          = aws_lambda_function.wake.function_name
  principal              = "*"
  function_url_auth_type = "NONE"
}

# Desde out/2025 a AWS exige as duas permissões (InvokeFunctionUrl + InvokeFunction) para
# qualquer Function URL — sem esta segunda, a própria URL responde 403 antes de chamar o código
# (https://docs.aws.amazon.com/lambda/latest/dg/urls-auth.html, seção "Using the NONE auth type").
resource "aws_lambda_permission" "wake_public_invoke" {
  statement_id             = "AllowPublicInvokeViaFunctionUrl"
  action                   = "lambda:InvokeFunction"
  function_name            = aws_lambda_function.wake.function_name
  principal                = "*"
  invoked_via_function_url = true
}

# ---------------------------------------------------------------- desligar por ociosidade

resource "aws_iam_role" "idle_shutdown" {
  name               = "${local.name}-idle-shutdown"
  assume_role_policy = data.aws_iam_policy_document.lambda_trust.json
}

resource "aws_iam_role_policy" "idle_shutdown" {
  name = "stop-instance-and-read-metrics"
  role = aws_iam_role.idle_shutdown.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "ec2:DescribeInstances"
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = "ec2:StopInstances"
        Resource = aws_instance.app.arn
      },
      {
        Effect   = "Allow"
        Action   = ["cloudwatch:GetMetricStatistics"]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "arn:${local.partition}:logs:${local.region}:${local.account_id}:log-group:/aws/lambda/${local.name}-idle-shutdown:*"
      }
    ]
  })
}

resource "aws_lambda_function" "idle_shutdown" {
  function_name    = "${local.name}-idle-shutdown"
  role             = aws_iam_role.idle_shutdown.arn
  handler          = "handler.handler"
  runtime          = "python3.13"
  architectures    = ["arm64"]
  timeout          = 30
  memory_size      = 128
  filename         = data.archive_file.idle_shutdown.output_path
  source_code_hash = data.archive_file.idle_shutdown.output_base64sha256

  environment {
    variables = {
      INSTANCE_ID     = aws_instance.app.id
      DISTRIBUTION_ID = aws_cloudfront_distribution.demo.id
      IDLE_MINUTES    = tostring(var.idle_timeout_minutes)
    }
  }
}

resource "aws_scheduler_schedule" "idle_shutdown" {
  name                = "${local.name}-idle-shutdown"
  schedule_expression = "rate(${var.idle_check_interval_minutes} minutes)"
  flexible_time_window { mode = "OFF" }

  target {
    arn      = aws_lambda_function.idle_shutdown.arn
    role_arn = aws_iam_role.idle_shutdown_invoke.arn
  }
}

resource "aws_iam_role" "idle_shutdown_invoke" {
  name = "${local.name}-idle-shutdown-scheduler"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "scheduler.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "idle_shutdown_invoke" {
  name = "invoke-lambda"
  role = aws_iam_role.idle_shutdown_invoke.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "lambda:InvokeFunction"
      Resource = aws_lambda_function.idle_shutdown.arn
    }]
  })
}
