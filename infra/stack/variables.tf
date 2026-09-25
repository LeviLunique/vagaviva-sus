variable "project" {
  type    = string
  default = "vagaviva"
}

variable "environment" {
  description = "hml ou prod."
  type        = string
  validation {
    condition     = contains(["hml", "prod"], var.environment)
    error_message = "environment deve ser hml ou prod."
  }
}

# ---------------------------------------------------------------- rede

variable "vpc_cidr" {
  type = string
}

variable "az_count" {
  description = "Quantidade de zonas de disponibilidade (mínimo 2: ALB e RDS Multi-AZ exigem)."
  type        = number
  default     = 2
}

variable "nat_mode" {
  description = "none (tasks em subnets públicas sem ingresso), single (1 NAT) ou per_az (1 NAT por AZ, HA)."
  type        = string
  default     = "per_az"
  validation {
    condition     = contains(["none", "single", "per_az"], var.nat_mode)
    error_message = "nat_mode deve ser none, single ou per_az."
  }
}

variable "enable_flow_logs" {
  type    = bool
  default = true
}

# ---------------------------------------------------------------- banco

variable "db_engine_version" {
  type    = string
  default = "17"
}

variable "db_instance_class" {
  type = string
}

variable "db_allocated_storage" {
  type    = number
  default = 20
}

variable "db_multi_az" {
  type = bool
}

variable "db_backup_retention_days" {
  type    = number
  default = 7
}

variable "db_deletion_protection" {
  type = bool
}

# ---------------------------------------------------------------- aplicação

variable "ecr_repository_name" {
  type    = string
  default = "vagaviva-api"
}

variable "initial_image_tag" {
  description = "Tag usada somente na criação; depois o pipeline controla a tag via SSM (/vagaviva/<env>/api/image-tag)."
  type        = string
  default     = "bootstrap"
}

variable "app_cpu" {
  type = number
}

variable "app_memory" {
  type = number
}

variable "app_min_count" {
  type = number
}

variable "app_max_count" {
  type = number
}

variable "use_fargate_spot" {
  description = "Usa Fargate Spot (até ~70% mais barato). Recomendado apenas fora de produção."
  type        = bool
  default     = false
}

variable "enable_execute_command" {
  type    = bool
  default = false
}

variable "enable_otel_collector" {
  description = "Sidecar ADOT (OpenTelemetry) exportando traces para X-Ray e métricas (EMF) para CloudWatch."
  type        = bool
  default     = true
}

variable "otel_collector_image" {
  type    = string
  default = "public.ecr.aws/aws-observability/aws-otel-collector:v0.50.0"
}

variable "spring_profiles" {
  type    = string
  default = "aws"
}

variable "log_retention_days" {
  type    = number
  default = 30
}

variable "container_insights" {
  type    = bool
  default = true
}

# ---------------------------------------------------------------- borda

variable "enable_waf" {
  type    = bool
  default = true
}

variable "waf_rate_limit_per_ip" {
  description = "Requisições por IP em 5 minutos (todas as rotas)."
  type        = number
  default     = 2000
}

variable "waf_public_rate_limit_per_ip" {
  description = "Requisições por IP em 5 minutos nas rotas públicas (/api/v1/public/ e links do paciente)."
  type        = number
  default     = 100
}

# ---------------------------------------------------------------- observabilidade

variable "alarm_email" {
  description = "E-mail para alarmes (via TF_VAR_alarm_email). Vazio = sem assinatura."
  type        = string
  default     = ""
}
