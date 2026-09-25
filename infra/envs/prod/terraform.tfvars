# Produção: alta disponibilidade em 2 AZs, tasks e banco isolados em subnets privadas.
vpc_cidr         = "10.50.0.0/16"
nat_mode         = "per_az"
enable_flow_logs = true

db_instance_class        = "db.t4g.medium"
db_multi_az              = true
db_backup_retention_days = 14
db_deletion_protection   = true

app_cpu                = 1024
app_memory             = 2048
app_min_count          = 2
app_max_count          = 10
use_fargate_spot       = false
enable_execute_command = false
spring_profiles        = "aws"
log_retention_days     = 90
enable_waf             = true
