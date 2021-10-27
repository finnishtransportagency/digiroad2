# create
# aws cloudformation create-stack --region eu-west-1 --stack-name devFargate --on-failure DELETE --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/fargateService/alb_ecs.yaml --parameters file://aws/cloud-formation/fargateService/parameter.json --profile vaylaapp
# update devFargate

aws cloudformation create-change-set --stack-name devFargate --change-set-name UpdateStack --template-body file://aws/cloud-formation/fargateService/alb_ecs.yaml  --parameters file://aws/cloud-formation/fargateService/parameter.json --profile vaylaapp

aws cloudformation update-stack --stack-name devFargate --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/fargateService/alb_ecs.yaml --parameters file://aws/cloud-formation/fargateService/parameter.json --profile vaylaapp
