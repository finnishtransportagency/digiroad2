# create
# aws cloudformation create-stack --stack-name devpipeline --on-failure DELETE --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/cicd/cicd-github.yaml --parameters file://aws/cloud-formation/cicd/parameter.json --profile vaylaapp

# update devpipeline
aws cloudformation update-stack --stack-name devpipeline --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/cicd/cicd-github.yaml --parameters file://aws/cloud-formation/cicd/parameter.json --profile vaylaapp
