variable "region" {
  type    = string
  default = "sa-east-1"
}

variable "vpc_cidr" {
  type    = string
  default = "10.60.0.0/24"
}

variable "instance_type" {
  description = "t4g.medium (4 GiB) roda API + PostgreSQL com folga; t4g.small (2 GiB) é o mínimo viável."
  type        = string
  default     = "t4g.medium"
}

variable "root_volume_gb" {
  description = "Disco único (EBS gp3): app + dados do PostgreSQL. Persiste entre stop/start (só some se a instância for destruída)."
  type        = number
  default     = 20
}

variable "docker_compose_version" {
  type    = string
  default = "v5.5.1"
}

variable "ecr_repository_name" {
  type    = string
  default = "vagaviva-api"
}

variable "initial_image_tag" {
  description = "Tag usada somente na criação; depois o pipeline/scripts controlam via SSM (/vagaviva/demo/api/image-tag)."
  type        = string
  default     = "bootstrap"
}

variable "initial_public_base_url" {
  description = <<-EOT
    Só usada na criação (evita ciclo: a instância ainda não conhece a URL da CloudFront, que
    só existe depois da instância). Depois do primeiro apply, scripts/aws/infra.sh atualiza o
    parâmetro SSM (/vagaviva/demo/api/public-base-url) com a URL real.
  EOT
  type        = string
  default     = "pending"
}

variable "spring_profiles" {
  type    = string
  default = "aws,demo"
}

variable "idle_timeout_minutes" {
  description = "Minutos sem requisições na CloudFront até a instância ser desligada automaticamente."
  type        = number
  default     = 30
}

variable "idle_check_interval_minutes" {
  description = "Frequência da verificação de ociosidade (EventBridge Scheduler)."
  type        = number
  default     = 10
}

variable "enable_waf" {
  description = "Desligado por padrão no perfil demo (custo); mantido como opção — ver ADR-0012."
  type        = bool
  default     = false
}

variable "log_retention_days" {
  type    = number
  default = 14
}

variable "alarm_email" {
  type    = string
  default = ""
}
