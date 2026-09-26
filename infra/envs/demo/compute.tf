# EC2 unica (app + PostgreSQL via Docker Compose). Sem SSH: administracao via SSM Session Manager.

data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

data "aws_ecr_repository" "api" {
  name = var.ecr_repository_name
}

locals {
  ecr_registry = "${local.account_id}.dkr.ecr.${local.region}.amazonaws.com"
}

resource "aws_cloudwatch_log_group" "instance" {
  name              = "/ec2/${local.name}"
  retention_in_days = var.log_retention_days
}

# Tag da imagem em execucao: criada aqui, atualizada pelos scripts de deploy do perfil demo.
resource "aws_ssm_parameter" "image_tag" {
  name           = "/${local.project}/${local.env}/api/image-tag"
  type           = "String"
  insecure_value = var.initial_image_tag

  lifecycle {
    ignore_changes = [insecure_value]
  }
}

# Quebra o ciclo instância -> CloudFront -> instância: a URL real só existe depois que a
# instância (origem da CloudFront) já foi criada. scripts/aws/infra.sh preenche o valor real
# logo após o primeiro apply; o refresh.sh busca este parâmetro a cada boot.
resource "aws_ssm_parameter" "public_base_url" {
  name  = "/${local.project}/${local.env}/api/public-base-url"
  type  = "String"
  value = var.initial_public_base_url

  lifecycle {
    ignore_changes = [value]
  }
}

locals {
  docker_compose_content = templatefile("${path.module}/templates/docker-compose.demo.yml.tftpl", {
    region                   = local.region
    spring_profiles          = var.spring_profiles
    notifications_queue_name = aws_sqs_queue.notifications.name
    log_group_name           = aws_cloudwatch_log_group.instance.name
  })

  user_data = templatefile("${path.module}/templates/user-data.sh.tftpl", {
    docker_compose_version    = var.docker_compose_version
    docker_compose_content    = local.docker_compose_content
    region                    = local.region
    ecr_registry              = local.ecr_registry
    ecr_repository_name       = var.ecr_repository_name
    image_tag_parameter       = aws_ssm_parameter.image_tag.name
    app_secret_arn            = aws_secretsmanager_secret.app.arn
    db_secret_arn             = aws_secretsmanager_secret.db.arn
    public_base_url_parameter = aws_ssm_parameter.public_base_url.name
  })
}

# ---------------------------------------------------------------- IAM

data "aws_iam_policy_document" "instance_trust" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "instance" {
  name               = "${local.name}-instance"
  assume_role_policy = data.aws_iam_policy_document.instance_trust.json
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:${local.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "instance" {
  statement {
    sid       = "EcrAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EcrPull"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = [data.aws_ecr_repository.api.arn]
  }

  statement {
    sid       = "ReadParameters"
    actions   = ["ssm:GetParameter"]
    resources = [aws_ssm_parameter.image_tag.arn, aws_ssm_parameter.public_base_url.arn]
  }

  statement {
    sid       = "ReadSecrets"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.app.arn, aws_secretsmanager_secret.db.arn]
  }

  statement {
    sid = "NotificationQueues"
    actions = [
      "sqs:SendMessage",
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:ChangeMessageVisibility",
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl",
    ]
    resources = [aws_sqs_queue.notifications.arn, aws_sqs_queue.notifications_dlq.arn]
  }

  statement {
    sid       = "SmsPublish"
    actions   = ["sns:Publish"]
    resources = ["*"]
  }

  statement {
    sid = "Telemetry"
    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents",
      "logs:DescribeLogStreams",
      "cloudwatch:PutMetricData",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "instance" {
  name   = "runtime"
  role   = aws_iam_role.instance.id
  policy = data.aws_iam_policy_document.instance.json
}

resource "aws_iam_instance_profile" "instance" {
  name = local.name
  role = aws_iam_role.instance.name
}

# ---------------------------------------------------------------- instancia

resource "aws_instance" "app" {
  ami                    = data.aws_ssm_parameter.al2023_arm64.value
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.instance.id]
  iam_instance_profile   = aws_iam_instance_profile.instance.name
  user_data              = local.user_data
  # user_data so muda se a versao do compose ou os ARNs mudarem (raro); ARNs de segredo mudam
  # se os segredos forem recriados. Recriar a instancia nesse caso e aceitavel (perfil demo).

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_gb
    encrypted             = true
    delete_on_termination = true
  }

  metadata_options {
    http_tokens                 = "required" # IMDSv2 obrigatorio
    http_put_response_hop_limit = 2          # o SDK dentro do conteiner (rede bridge) precisa de 2 saltos
  }

  tags = { Name = local.name }
}
