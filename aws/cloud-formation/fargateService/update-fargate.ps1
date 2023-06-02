aws cloudformation update-stack --stack-name devFargate --template-body file://aws/cloud-formation/fargateService/alb_ecs.yaml --profile vaylaapp --parameters file://aws\cloud-formation\fargateService\dev\DEV-alb-ecs-parameter.json

aws cloudformation create-stack --stack-name devFargateForTesting --region eu-west-1 --on-failure DELETE --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/fargateService/alb_ecs.yaml --profile vaylaapp --parameters file://aws\cloud-formation\fargateService\dev\DEV-alb-ecs-parameter2.json

aws cloudformation update-stack --stack-name devFargateForTesting --template-body file://aws/cloud-formation/fargateService/alb_ecs.yaml --profile vaylaapp --parameters file://aws\cloud-formation\fargateService\dev\DEV-alb-ecs-parameter2.json


aws cloudformation update-stack --stack-name qa-Fargate --template-body file://aws/cloud-formation/fargateService/alb_ecs.yaml --profile vaylaapp --parameters file://aws\cloud-formation\fargateService\qa\QA-alb-ecs-parameter.json