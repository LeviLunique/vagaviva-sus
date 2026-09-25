# PostgreSQL 17 (RDS) em subnets isoladas, criptografado em repouso e com TLS obrigatório.

resource "aws_db_subnet_group" "this" {
  name       = local.name
  subnet_ids = aws_subnet.database[*].id
}

resource "aws_security_group" "db" {
  name        = "${local.name}-db"
  description = "PostgreSQL acessivel somente pelas tasks da API"
  vpc_id      = aws_vpc.this.id
  tags        = { Name = "${local.name}-db" }
}

resource "aws_vpc_security_group_ingress_rule" "db_from_app" {
  security_group_id            = aws_security_group.db.id
  referenced_security_group_id = aws_security_group.app.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "API"
}

resource "aws_db_parameter_group" "this" {
  name   = local.name
  family = "postgres17"

  # Parâmetro estático: aplicado no próximo reboot (evita diferença perpétua no plan).
  parameter {
    name         = "rds.force_ssl"
    value        = "1"
    apply_method = "pending-reboot"
  }

  # Registra consultas lentas (> 500 ms) no CloudWatch Logs.
  parameter {
    name  = "log_min_duration_statement"
    value = "500"
  }
}

resource "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_db_instance" "this" {
  identifier     = local.name
  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  db_name  = "vagaviva"
  username = "vagaviva"
  password = random_password.db.result
  port     = 5432

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_allocated_storage * 5
  storage_type          = "gp3"
  storage_encrypted     = true

  multi_az               = var.db_multi_az
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.db.id]
  parameter_group_name   = aws_db_parameter_group.this.name
  publicly_accessible    = false

  backup_retention_period         = var.db_backup_retention_days
  backup_window                   = "05:00-06:00"         # 02h-03h (Brasília)
  maintenance_window              = "sun:06:30-sun:07:30" # domingo 03h30 (Brasília)
  auto_minor_version_upgrade      = true
  copy_tags_to_snapshot           = true
  enabled_cloudwatch_logs_exports = ["postgresql"]
  deletion_protection             = var.db_deletion_protection
  skip_final_snapshot             = !var.db_deletion_protection
  final_snapshot_identifier       = "${local.name}-final"
  apply_immediately               = !local.is_prod
}

resource "aws_secretsmanager_secret" "db" {
  name                    = "${local.name}/db"
  description             = "Credenciais do PostgreSQL da API (${var.environment})"
  recovery_window_in_days = local.is_prod ? 7 : 0
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    username = aws_db_instance.this.username
    password = random_password.db.result
    host     = aws_db_instance.this.address
    port     = aws_db_instance.this.port
    dbname   = aws_db_instance.this.db_name
  })
}
