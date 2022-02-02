# update job definition
aws batch register-job-definition --profile vaylaapp --region eu-west-1 --cli-input-json file://aws/cloud-formation/batchSystem/QAbatchJobDefinition.json
aws batch register-job-definition --profile vaylaapp --region eu-west-1 --cli-input-json file://aws/cloud-formation/batchSystem/DEVbatchJobDefinition.json

# create
#aws cloudformation create-stack --region eu-west-1 --stack-name batchSystem --on-failure DELETE --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/batchSystem/batchSystem.yaml --parameters file://aws/cloud-formation/batchSystem/parameter.json --profile vaylaapp
aws cloudformation update-stack --stack-name batchSystem --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/batchSystem/batchSystem.yaml --parameters file://aws/cloud-formation/batchSystem/parameter.json --profile vaylaapp