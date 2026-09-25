terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Backend parcial: scripts/aws/infra.sh informa bucket/key/region no init.
  backend "s3" {}
}

locals {
  tags = {
    Project     = "vagaviva"
    Environment = "hml"
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

# WAF de CloudFront só pode ser criado em us-east-1.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
  default_tags {
    tags = local.tags
  }
}

module "stack" {
  source = "../../stack"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  environment = "hml"
  alarm_email = var.alarm_email

  vpc_cidr         = var.vpc_cidr
  nat_mode         = var.nat_mode
  enable_flow_logs = var.enable_flow_logs

  db_instance_class        = var.db_instance_class
  db_multi_az              = var.db_multi_az
  db_backup_retention_days = var.db_backup_retention_days
  db_deletion_protection   = var.db_deletion_protection

  initial_image_tag      = var.initial_image_tag
  app_cpu                = var.app_cpu
  app_memory             = var.app_memory
  app_min_count          = var.app_min_count
  app_max_count          = var.app_max_count
  use_fargate_spot       = var.use_fargate_spot
  enable_execute_command = var.enable_execute_command
  spring_profiles        = var.spring_profiles
  log_retention_days     = var.log_retention_days
  enable_waf             = var.enable_waf
}
