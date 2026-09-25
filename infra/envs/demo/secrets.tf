# Segredos da aplicação (mesmo formato do perfil hml/prod: infra/stack/database.tf e app.tf).
# O banco roda no mesmo host (container "db" do Docker Compose), então o segredo do banco
# guarda usuario/senha fixos gerados uma vez — o Postgres só usa POSTGRES_PASSWORD na primeira
# inicialização do volume; reinicios seguintes (mesmo com a instância desligando/religando)
# continuam usando a senha já gravada no volume, que é sempre a mesma vinda deste segredo.

resource "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "db" {
  name                    = "${local.name}/db"
  description             = "Credenciais do PostgreSQL (perfil demo, roda na mesma instância da API)"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    username = "vagaviva"
    password = random_password.db.result
    host     = "db"
    port     = 5432
    dbname   = "vagaviva"
  })
}

resource "tls_private_key" "jwt" {
  algorithm = "RSA"
  rsa_bits  = 3072
}

resource "random_password" "bootstrap_admin" {
  length           = 24
  special          = true
  override_special = "!@#%*-_"
}

resource "random_password" "demo_users" {
  length           = 20
  special          = true
  override_special = "!@#%*-_"
}

resource "aws_secretsmanager_secret" "app" {
  name                    = "${local.name}/app"
  description             = "Segredos da API (demo): chave JWT, senha do admin inicial e dos usuarios de demonstracao"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "app" {
  secret_id = aws_secretsmanager_secret.app.id
  secret_string = jsonencode({
    JWT_PRIVATE_KEY          = base64encode(tls_private_key.jwt.private_key_pem_pkcs8)
    BOOTSTRAP_ADMIN_PASSWORD = random_password.bootstrap_admin.result
    DEMO_USERS_PASSWORD      = random_password.demo_users.result
  })
}
