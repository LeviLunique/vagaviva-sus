"""Executada periodicamente (EventBridge Scheduler). Desliga a instancia se nao houve nenhuma
requisicao na distribuicao CloudFront na janela de ociosidade configurada. As metricas da
CloudFront sao publicadas sempre em us-east-1, independente da regiao da distribuicao.
"""
import datetime
import os

import boto3

INSTANCE_ID = os.environ["INSTANCE_ID"]
DISTRIBUTION_ID = os.environ["DISTRIBUTION_ID"]
IDLE_MINUTES = int(os.environ["IDLE_MINUTES"])

ec2 = boto3.client("ec2")
cloudwatch = boto3.client("cloudwatch", region_name="us-east-1")


def handler(event, context):
    state = ec2.describe_instances(InstanceIds=[INSTANCE_ID])["Reservations"][0]["Instances"][0]["State"]["Name"]
    if state != "running":
        return {"action": "none", "reason": f"instancia em estado '{state}'"}

    now = datetime.datetime.now(datetime.timezone.utc)
    metrics = cloudwatch.get_metric_statistics(
        Namespace="AWS/CloudFront",
        MetricName="Requests",
        Dimensions=[
            {"Name": "DistributionId", "Value": DISTRIBUTION_ID},
            {"Name": "Region", "Value": "Global"},
        ],
        StartTime=now - datetime.timedelta(minutes=IDLE_MINUTES),
        EndTime=now,
        Period=IDLE_MINUTES * 60,
        Statistics=["Sum"],
    )
    requests = sum(p["Sum"] for p in metrics["Datapoints"]) if metrics["Datapoints"] else 0

    if requests > 0:
        return {"action": "none", "reason": f"{requests} requisicoes nos ultimos {IDLE_MINUTES} min"}

    ec2.stop_instances(InstanceIds=[INSTANCE_ID])
    return {"action": "stopped", "reason": f"nenhuma requisicao nos ultimos {IDLE_MINUTES} min"}
