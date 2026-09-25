# Homologação: custo mínimo, mesma arquitetura de produção.
vpc_cidr         = "10.40.0.0/16"
nat_mode         = "none" # tasks em subnet pública, sem regra de entrada além do ALB
enable_flow_logs = false

db_instance_class        = "db.t4g.micro"
db_multi_az              = false
db_backup_retention_days = 1
db_deletion_protection   = false

app_cpu                = 512
app_memory             = 1024
app_min_count          = 1
app_max_count          = 2
use_fargate_spot       = true
enable_execute_command = true
spring_profiles        = "aws,demo"
log_retention_days     = 14
enable_waf             = true
