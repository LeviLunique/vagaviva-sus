variable "project" {
  description = "Prefixo de todos os recursos."
  type        = string
  default     = "vagaviva"
}

variable "region" {
  description = "Região AWS (São Paulo: dados de saúde permanecem no Brasil)."
  type        = string
  default     = "sa-east-1"
}

variable "github_owner" {
  type    = string
  default = "LeviLunique"
}

variable "github_repo" {
  type    = string
  default = "vagaviva-sus"
}

variable "github_owner_id" {
  description = "ID numérico do dono no GitHub (gh api users/<owner> --jq .id)."
  type        = string
  default     = "17887087"
}

variable "github_repo_id" {
  description = "ID numérico do repositório (gh api repos/<owner>/<repo> --jq .id)."
  type        = string
  default     = "1386619867"
}

variable "environments" {
  description = "Ambientes que recebem role de deploy (devem existir como Environments no GitHub)."
  type        = list(string)
  default     = ["hml", "prod"]
}

variable "create_github_oidc_provider" {
  description = "true somente se a conta ainda não tiver o provedor OIDC do GitHub."
  type        = bool
  default     = false
}

variable "monthly_budget_usd" {
  description = "Orçamento mensal (USD) dos recursos com tag Project=vagaviva."
  type        = number
  default     = 150
}

variable "budget_alert_email" {
  description = "E-mail que recebe alertas de orçamento (informado via TF_VAR_budget_alert_email)."
  type        = string
  default     = ""
}
