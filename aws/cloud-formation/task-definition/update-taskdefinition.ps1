# example to create DEV
#aws cloudformation create-stack --on-failure DELETE --stack-name dev-Taskdefinition --template-body file://aws/cloud-formation/taskdefinition/dev-create-taskdefinition.yaml --profile vaylaapp --parameters file://aws/cloud-formation/taskdefinition/dev-taskdefinition-parameter.json --capabilities CAPABILITY_IAM

# DEV
aws cloudformation update-stack --stack-name dev-Taskdefinition --template-body file://aws/cloud-formation/task-definition/dev-create-taskdefinition.yaml --profile vaylaapp --parameters file://aws/cloud-formation/task-definition/dev-taskdefinition-parameter.json --capabilities CAPABILITY_IAM
# QA
aws cloudformation update-stack --stack-name qa-Taskdefinition --template-body file://aws/cloud-formation/task-definition/qa-create-taskdefinition.yaml --profile vaylaapp --parameters file://aws/cloud-formation/task-definition/qa-taskdefinition-parameter.json --capabilities CAPABILITY_IAM
