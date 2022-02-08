aws cloudformation update-stack --stack-name qa-Taskdefinition --template-body file://aws/cloud-formation/task-definition/qa-create-taskdefiniton.yaml --profile vaylaapp --parameters file://aws/cloud-formation/task-definition/qa-taskdefinition-paramer.json --capabilities CAPABILITY_IAM

aws cloudformation update-stack --stack-name dev-Taskdefinition --template-body file://aws/cloud-formation/task-definition/dev-create-taskdefiniton.yaml --profile vaylaapp --parameters file://aws/cloud-formation/task-definition/dev-taskdefinition-paramer.json --capabilities CAPABILITY_IAM
