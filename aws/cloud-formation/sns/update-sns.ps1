# example to create DEV and QA
#aws cloudformation create-stack --profile vaylaapp --stack-name SNS-notifications --region eu-west-1 --on-failure DELETE --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/sns/snsNotifications.yaml --parameters file://aws/cloud-formation/sns/dev-sns-parameter.json

#DEV and QA
aws cloudformation update-stack --stack-name SNS-notifications --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/sns/snsNotifications.yaml --parameters file://aws/cloud-formation/sns/dev-sns-parameter.json
