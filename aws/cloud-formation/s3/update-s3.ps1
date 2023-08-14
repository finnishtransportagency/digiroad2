# example to create dev
#aws cloudformation create-stack --stack-name digiroad-dev-api-s3 --region eu-west-1 --on-failure DELETE --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/s3/digiroad2-s3.yaml --profile vaylaapp --parameters file://aws\cloud-formation\fargateService\dev\DEV-s3-parameter.json

#DEV
aws cloudformation update-stack --stack-name digiroad-dev-api-s3 --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/s3/digiroad2-s3.yaml --parameters file://aws/cloud-formation/s3/DEV-s3-parameter.json
#QA
aws cloudformation update-stack --stack-name qa-digiroad-api-s3 --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/s3/digiroad2-s3.yaml --parameters file://aws/cloud-formation/s3/QA-s3-parameter.json
