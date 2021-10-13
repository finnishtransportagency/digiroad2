# create
#aws cloudformation create-stack --region eu-west-1 --stack-name qaTaskDefinitionRight --on-failure DELETE --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/fargateService/ECSTaskExecutionRole.yaml --profile vaylaapp
#aws cloudformation create-stack --region eu-west-1 --stack-name qaFargate --on-failure DELETE --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/fargateService/alb_ecs.yaml --parameters file://aws/cloud-formation/qa/parameter.json --profile vaylaapp

#aws cloudformation validate-template --template-body file://aws/cloud-formation/fargateService/alb_ecs.yaml --profile vaylaapp

# update qaFargate
aws cloudformation update-stack --stack-name qaFargate  --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/fargateService/alb_ecs.yaml --parameters file://aws/cloud-formation/qa/parameter.json --profile vaylaapp




