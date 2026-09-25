"""Funcao "acordar": e a origem secundaria da CloudFront (origin group). So e chamada quando a
EC2 (origem primaria) nao responde, ou seja, exatamente quando ela esta desligada. Liga a
instancia (chamada idempotente: se ja estiver ligada/ligando, so devolve o estado atual) e
devolve uma pagina HTML que se recarrega sozinha ate a aplicacao real responder.

A URL da funcao e publica (AuthType NONE), mas so age se receber o cabecalho secreto que a
CloudFront injeta nesta origem especifica (custom_header em edge.tf; valor gerado pelo Terraform
e nunca commitado no codigo-fonte). Sem o cabecalho correto, so devolve a pagina, sem ligar nada
- ver lambda.tf para o raciocinio completo (Origin Access Control + origin group de failover se
mostrou pouco confiavel nessa combinacao especifica).
"""
import os

import boto3

INSTANCE_ID = os.environ["INSTANCE_ID"]
SHARED_SECRET_HEADER_VALUE = os.environ["SHARED_SECRET_HEADER_VALUE"]
ec2 = boto3.client("ec2")

PAGE = """<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  <meta http-equiv="refresh" content="8">
  <title>VagaViva - iniciando</title>
  <style>
    body {{ font-family: system-ui, sans-serif; background: #0b3d2e; color: #fff;
            display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }}
    .card {{ text-align: center; max-width: 420px; padding: 24px; }}
    .spinner {{ width: 40px; height: 40px; margin: 0 auto 16px; border-radius: 50%;
                border: 4px solid rgba(255,255,255,.25); border-top-color: #fff;
                animation: spin 1s linear infinite; }}
    @keyframes spin {{ to {{ transform: rotate(360deg); }} }}
    small {{ opacity: .7; }}
  </style>
</head>
<body>
  <div class="card">
    <div class="spinner"></div>
    <h1>Iniciando o VagaViva&hellip;</h1>
    <p>O ambiente de demonstracao estava em espera para economizar recursos e acabou de ser
       ligado. Isso leva cerca de 60 a 90 segundos.</p>
    <p>Esta pagina atualiza sozinha a cada 8 segundos.</p>
    <small>Status da instancia: {state}</small>
  </div>
</body>
</html>"""


def handler(event, context):
    headers = {k.lower(): v for k, v in (event.get("headers") or {}).items()}
    if headers.get("x-vagaviva-wake-secret") != SHARED_SECRET_HEADER_VALUE:
        return {
            "statusCode": 403,
            "headers": {"content-type": "text/plain; charset=utf-8"},
            "body": "Forbidden",
        }

    state = "desconhecido"
    try:
        resp = ec2.describe_instances(InstanceIds=[INSTANCE_ID])
        state = resp["Reservations"][0]["Instances"][0]["State"]["Name"]
        if state in ("stopped", "stopping"):
            ec2.start_instances(InstanceIds=[INSTANCE_ID])
            state = "iniciando"
    except Exception as exc:  # nao deixamos a pagina de espera quebrar por erro transitorio
        state = f"erro ao consultar/iniciar ({exc.__class__.__name__})"

    body = PAGE.format(state=state)
    return {
        "statusCode": 200,
        "headers": {"content-type": "text/html; charset=utf-8", "cache-control": "no-store"},
        "body": body,
    }
