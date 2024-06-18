# create
aws cloudformation create-stack  --region eu-west-1 --stack-name code-artifact-stack --on-failure DELETE --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/cicd/codeArtifact/codeArtifact.yaml --parameters file://aws/cloud-formation/cicd/codeArtifact/codeArtifact-parameter.json --profile vaylaapp

# update
aws cloudformation update-stack --stack-name code-artifact-stack --capabilities CAPABILITY_NAMED_IAM --template-body file://aws/cloud-formation/cicd/codeArtifact/codeArtifact.yaml --parameters file://aws/cloud-formation/cicd/codeArtifact/codeArtifact-parameter.json --profile vaylaapp
