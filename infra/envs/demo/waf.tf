# WAF opcional (desligado por padrao no perfil demo - custo; ver var.enable_waf e ADR-0012).
# Versao enxuta do WAF do perfil hml/prod (infra/stack/edge.tf): regras gerenciadas da AWS +
# um unico limite de taxa global (sem a distincao de rota publica, para manter simples).

resource "aws_wafv2_web_acl" "demo" {
  count    = var.enable_waf ? 1 : 0
  provider = aws.us_east_1
  name     = "${local.name}-api"
  scope    = "CLOUDFRONT"

  default_action {
    allow {}
  }

  rule {
    name     = "common-rule-set"
    priority = 0
    override_action {
      none {}
    }
    statement {
      managed_rule_group_statement {
        vendor_name = "AWS"
        name        = "AWSManagedRulesCommonRuleSet"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "common-rule-set"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "known-bad-inputs"
    priority = 1
    override_action {
      none {}
    }
    statement {
      managed_rule_group_statement {
        vendor_name = "AWS"
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "known-bad-inputs"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "rate-limit-global"
    priority = 2
    action {
      block {}
    }
    statement {
      rate_based_statement {
        limit              = 2000
        aggregate_key_type = "IP"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "rate-limit-global"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${local.name}-api"
    sampled_requests_enabled   = true
  }
}
