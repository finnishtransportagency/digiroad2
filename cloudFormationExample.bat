aws cloudformation --region eu-west-1 create-stack --stack-name stacknamne --on-failure DELETE --capabilities CAPABILITY_NAMED_IAM --template-body file://absolute path to file --parameters file://absolute path to file --profile vaylaapp

aws cloudformation --region eu-west-1 update-stack --stack-name stacknamne --on-failure DELETE --capabilities CAPABILITY_NAMED_IAM --template-body file://absolute path to file --parameters file://absolute path to file --profile vaylaapp

aws cloudformation --region eu-west-1 validate --stack-name stacknamne --on-failure DELETE --capabilities CAPABILITY_NAMED_IAM --template-body file://absolute path to file --parameters file://absolute path to file --profile vaylaapp
