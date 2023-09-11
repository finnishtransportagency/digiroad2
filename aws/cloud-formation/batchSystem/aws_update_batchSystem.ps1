# update job definition
aws batch register-job-definition --profile vaylaapp --region eu-west-1 --cli-input-json file://aws/cloud-formation/batchSystem/QAbatchJobDefinition.json
aws batch register-job-definition --profile vaylaapp --region eu-west-1 --cli-input-json file://aws/cloud-formation/batchSystem/DEVbatchJobDefinition.json

# example to create dev
#aws cloudformation create-stack --region eu-west-1 --stack-name DEV-batchSystem --on-failure DELETE --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/batchSystem/batchSystem.yaml --parameters file://aws/cloud-formation/batchSystem/dev-batch-system-parameter.json --profile vaylaapp

#Dev
aws cloudformation update-stack --stack-name DEV-batchSystem --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/batchSystem/batchSystem.yaml --parameters file://aws/cloud-formation/batchSystem/dev-batch-system-parameter.json --profile vaylaapp
#QA
aws cloudformation update-stack --stack-name QA-batchSystem --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/batchSystem/batchSystem.yaml --parameters file://aws/cloud-formation/batchSystem/qa-batch-system-parameter.json --profile vaylaapp
