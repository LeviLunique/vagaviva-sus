# Borda: CloudFront com duas origens.
#  - Primaria: a EC2, via VPC origin (rede interna da VPC, sem IP publico exposto).
#  - Secundaria (origin group / failover, só nas rotas de entrada GET): a Lambda "wake",
#    chamada automaticamente pela CloudFront quando a primaria falha (instancia desligada) -
#    ela liga a instancia e devolve a pagina de espera. Protegida por um cabeçalho secreto
#    (ver comentário em lambda.tf sobre por que não usamos OAC/SigV4 aqui). Ver nota sobre
#    origin groups e métodos HTTP mais abaixo, antes do bloco `default_cache_behavior`.

data "aws_cloudfront_cache_policy" "disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_origin_request_policy" "all_viewer_except_host" {
  name = "Managed-AllViewerExceptHostHeader"
}

data "aws_cloudfront_response_headers_policy" "security_headers" {
  name = "Managed-SecurityHeadersPolicy"
}

resource "aws_cloudfront_vpc_origin" "instance" {
  vpc_origin_endpoint_config {
    name                   = "${local.name}-app"
    arn                    = "arn:${local.partition}:ec2:${local.region}:${local.account_id}:instance/${aws_instance.app.id}"
    http_port              = 80
    https_port             = 443
    origin_protocol_policy = "http-only"

    origin_ssl_protocols {
      items    = ["TLSv1.2"]
      quantity = 1
    }
  }
}

resource "aws_cloudfront_distribution" "demo" {
  enabled         = true
  comment         = "${local.name} (perfil demo: EC2 unica com liga/desliga automatico)"
  price_class     = "PriceClass_All"
  http_version    = "http2and3"
  is_ipv6_enabled = true
  web_acl_id      = var.enable_waf ? aws_wafv2_web_acl.demo[0].arn : null

  origin {
    origin_id   = "instance"
    domain_name = aws_instance.app.private_dns

    vpc_origin_config {
      vpc_origin_id            = aws_cloudfront_vpc_origin.instance.id
      origin_keepalive_timeout = 5
      origin_read_timeout      = 30
    }
  }

  origin {
    origin_id   = "wake"
    domain_name = replace(replace(aws_lambda_function_url.wake.function_url, "https://", ""), "/", "")

    custom_header {
      name  = "X-VagaViva-Wake-Secret"
      value = random_password.wake_shared_secret.result
    }

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  # CloudFront não permite POST/PUT/PATCH/DELETE em um comportamento associado a um origin
  # group (não dá para reenviar uma escrita não confirmada a uma origem diferente). Por isso:
  #  - as rotas de "entrada" (Swagger, health, link do paciente) são só GET/HEAD/OPTIONS e vão
  #    pelo origin group -> religam a instância automaticamente quando ela está desligada;
  #  - o restante da API (a maioria, com POST/PUT/PATCH/DELETE) vai direto para a EC2, sem
  #    religamento automático - por isso o fluxo de demonstração é sempre abrir antes uma dessas
  #    rotas de entrada (ex.: o Swagger), esperar a página "iniciando" concluir, e só então usar
  #    o restante da API.
  origin_group {
    origin_id = "entry-points-with-wake-fallback"

    failover_criteria {
      status_codes = [500, 502, 503, 504]
    }

    member {
      origin_id = "instance"
    }
    member {
      origin_id = "wake"
    }
  }

  dynamic "ordered_cache_behavior" {
    for_each = toset([
      "/", "/swagger-ui.html", "/swagger-ui/*", "/v3/api-docs*",
      "/actuator/health*", "/actuator/info", "/p/*",
    ])
    content {
      path_pattern               = ordered_cache_behavior.value
      target_origin_id           = "entry-points-with-wake-fallback"
      viewer_protocol_policy     = "redirect-to-https"
      allowed_methods            = ["GET", "HEAD", "OPTIONS"]
      cached_methods             = ["GET", "HEAD"]
      cache_policy_id            = data.aws_cloudfront_cache_policy.disabled.id
      origin_request_policy_id   = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
      response_headers_policy_id = data.aws_cloudfront_response_headers_policy.security_headers.id
      compress                   = true
    }
  }

  default_cache_behavior {
    target_origin_id           = "instance"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods             = ["GET", "HEAD"]
    cache_policy_id            = data.aws_cloudfront_cache_policy.disabled.id
    origin_request_policy_id   = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
    response_headers_policy_id = data.aws_cloudfront_response_headers_policy.security_headers.id
    compress                   = true
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}
