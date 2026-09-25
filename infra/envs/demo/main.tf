# Perfil DEMO: apresentação de baixo custo (pós-graduação/hackathon), não é o design de produção.
#
# Diferenças em relação a hml/prod (infra/stack, ECS Fargate + RDS Multi-AZ):
#   - Uma única EC2 (t4g.medium) roda a API e o PostgreSQL juntos, via Docker Compose.
#   - Sem ALB, sem NAT Gateway, sem IP elástico: a CloudFront alcança a instância pela rede
#     interna da VPC (VPC origin apontando direto para o ARN da instância), sem expor a porta
#     80 publicamente — o security group só libera o SG gerenciado da própria CloudFront.
#   - A instância é desligada automaticamente quando ociosa (sem requisições no CloudFront por
#     30 min) e religada automaticamente no primeiro acesso seguinte (página de "aguarde" com
#     religamento automático), via duas funções Lambda pequenas.
#   - SQS real (não ElasticMQ) para manter a mensageria de verdade sem custo relevante.
#
# Ver docs/adr/0012-perfil-demo-ec2-unica.md para a justificativa completa.

terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source                = "hashicorp/aws"
      version               = "~> 6.0"
      configuration_aliases = [aws.us_east_1]
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.5"
    }
  }

  backend "s3" {}
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}
data "aws_partition" "current" {}

locals {
  project    = "vagaviva"
  env        = "demo"
  name       = "${local.project}-${local.env}"
  account_id = data.aws_caller_identity.current.account_id
  region     = data.aws_region.current.region
  partition  = data.aws_partition.current.partition
  tags = {
    Project     = local.project
    Environment = local.env
    ManagedBy   = "terraform"
    Repository  = "LeviLunique/vagaviva-sus"
  }
}

provider "aws" {
  region = var.region
  default_tags {
    tags = local.tags
  }
}

# O OAC de Lambda e o WAF de CloudFront (se habilitado) exigem us-east-1.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
  default_tags {
    tags = local.tags
  }
}
