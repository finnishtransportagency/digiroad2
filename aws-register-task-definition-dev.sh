#!/bin/bash
#
# Registers new version of the task definition.
# After running this you need to take this new task definition in use in service.
# If only the Docker container has changed, you don't need to run this.
# Usually you need to run this if environment variables have changed.
#
# Before running this script you must authenticate through Väylä SAML:
#
# python3 aws/login/vaylaAssumeRoleAWSCLI.py --username <Your Väylä username> --account 783354560127 --role ViiteAdmin --region eu-west-1
#
aws ecs register-task-definition --profile vaylaapp --region eu-west-1 --cli-input-json file://aws/task-definition/dev/task-definition.json

# After running this script, you can update the service to use this new task definition.
# Replace the <VERSION> with the new version. You can find the new version number from the JSON returned by the previous command:
#         "taskDefinitionArn": "arn:aws:ecs:eu-west-1:783354560127:task-definition/Viite-dev:<VERSION>",
#
# aws ecs update-service --profile vaylaapp --region eu-west-1 --cluster VIITE-ECS-Cluster --service viite-dev --task-definition Viite-dev:<VERSION> --force-new-deployment
