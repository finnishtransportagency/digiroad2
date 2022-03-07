# create
#aws cloudformation create-stack  --region eu-west-1 --stack-name qapipeline --on-failure DELETE --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/cicd/cicd-qa.yaml --parameters file://aws/cloud-formation/cicd/parameterQA.json --profile vaylaapp

# update qapipeline
aws cloudformation update-stack  --region eu-west-1 --stack-name qapipeline --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/cicd/qa/cicd-qa.yaml --parameters file://aws/cloud-formation/cicd/qa/QA-cicd-parameter.json --profile vaylaapp

