aws cloudformation --region eu-west-1 create-stack --stack-name stacknamne --on-failure DELETE --capabilities CAPABILITY_NAMED_IAM --template-body file://path to file --parameters file://path to file --profile vaylaapp

aws cloudformation --region eu-west-1 update-stack --stack-name stacknamne --capabilities CAPABILITY_NAMED_IAM --template-body file://path to file --parameters file://path to file --profile vaylaapp

aws cloudformation validate-template --template-body file://path to file --profile vaylaapp
