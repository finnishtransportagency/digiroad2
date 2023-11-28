# example to create enviroments
#aws cloudformation --region eu-west-1 create-stack --stack-name devCache --on-failure DELETE --capabilities CAPABILITY_NAMED_IAM  --template-body file://aws/cloud-formation/cache/cache.yaml --profile vaylaapp --parameters file://aws/cloud-formation/cache/DEV-cache-parameter.json
#aws cloudformation --region eu-west-1 create-stack --stack-name qaCache --on-failure DELETE --capabilities CAPABILITY_NAMED_IAM  --template-body file://aws/cloud-formation/cache/cache.yaml --profile vaylaapp --parameters file://aws/cloud-formation/cache/QA-cache-parameter.json

#DEV
aws cloudformation update-stack --stack-name devCache --template-body file://aws/cloud-formation/cache/cache.yaml --profile vaylaapp --parameters file://aws/cloud-formation/cache/DEV-cache-parameter.json
#QA
aws cloudformation update-stack --stack-name qaCache --template-body file://aws/cloud-formation/cache/cache.yaml --profile vaylaapp --parameters file://aws/cloud-formation/cache/QA-cache-parameter.json
