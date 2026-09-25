variable "region" {
  type    = string
  default = "sa-east-1"
}

variable "alarm_email" {
  description = "Informado via TF_VAR_alarm_email (não versionar e-mails)."
  type        = string
  default     = ""
}

variable "initial_image_tag" {
  type    = string
  default = "bootstrap"
}

variable "vpc_cidr" { type = string }
variable "nat_mode" { type = string }
variable "enable_flow_logs" { type = bool }
variable "db_instance_class" { type = string }
variable "db_multi_az" { type = bool }
variable "db_backup_retention_days" { type = number }
variable "db_deletion_protection" { type = bool }
variable "app_cpu" { type = number }
variable "app_memory" { type = number }
variable "app_min_count" { type = number }
variable "app_max_count" { type = number }
variable "use_fargate_spot" { type = bool }
variable "enable_execute_command" { type = bool }
variable "spring_profiles" { type = string }
variable "log_retention_days" { type = number }
variable "enable_waf" { type = bool }
