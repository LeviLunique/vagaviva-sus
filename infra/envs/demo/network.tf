# Rede mínima: 1 subnet pública, sem NAT Gateway (a instância tem IP público só para saída —
# pull de imagem no ECR, SSM, CloudWatch — a entrada na porta 80 é restrita ao SG da CloudFront).

data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = local.name }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = local.name }
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.this.id
  cidr_block              = var.vpc_cidr
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true
  tags                    = { Name = "${local.name}-public" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = "${local.name}-public" }
}

resource "aws_route" "internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# Endpoint gateway do S3 (gratuito): usado pelo ECR para as camadas de imagem, sem passar pela internet.
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${local.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.public.id]
  tags              = { Name = "${local.name}-s3" }
}

resource "aws_security_group" "instance" {
  name        = "${local.name}-instance"
  description = "EC2 unica (app + banco): porta 80 somente da VPC origin da CloudFront"
  vpc_id      = aws_vpc.this.id
  tags        = { Name = "${local.name}-instance" }
}

# Ao criar a VPC origin, a CloudFront provisiona na VPC o security group gerenciado
# "CloudFront-VPCOrigins-Service-SG". A instância aceita tráfego somente desse SG (não por
# CIDR), então mesmo tendo IP público a porta 80 não é alcançável de fora da CloudFront.
data "aws_security_group" "cloudfront_vpc_origin" {
  filter {
    name   = "group-name"
    values = ["CloudFront-VPCOrigins-Service-SG"]
  }
  filter {
    name   = "vpc-id"
    values = [aws_vpc.this.id]
  }
  depends_on = [aws_cloudfront_vpc_origin.instance]
}

resource "aws_vpc_security_group_ingress_rule" "instance_from_cloudfront" {
  security_group_id            = aws_security_group.instance.id
  referenced_security_group_id = data.aws_security_group.cloudfront_vpc_origin.id
  ip_protocol                  = "tcp"
  from_port                    = 80
  to_port                      = 80
  description                  = "CloudFront VPC origin"
}

resource "aws_vpc_security_group_egress_rule" "instance_all" {
  security_group_id = aws_security_group.instance.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
  description       = "ECR, SSM, CloudWatch, SQS, SNS"
}
