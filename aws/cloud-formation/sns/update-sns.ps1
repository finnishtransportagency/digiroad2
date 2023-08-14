#DEV and QA
aws cloudformation update-stack --stack-name digiroad-dev-api-s3 --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/sns/snsNotifications.yaml --parameters file://aws/cloud-formation/sns/dev-sns-parameter.json
