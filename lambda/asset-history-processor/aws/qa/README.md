# Testiympäristön pystytys

## Siirry Digiroad projektin juuresta lambda funktion omaan kansioon
```
cd lambda/asset-history-processor
```

## AWS CLI komennot

*HUOM.* Tarkista ennen jokaista create-stack komentoa parametritiedostojen sisältö.

### Luo ECR repository
```
aws cloudformation create-stack \
--stack-name [esim. qa-digiroad2-asset-history-processor-lambda-ecr] \
--template-body file://aws/cloudformation/ecr/ecr.yaml \
--parameters ParameterKey=Environment,ParameterValue=qa \
--tags file://aws/qa/tags.json
```

### Vie ensimmäinen palvelun image uuteen ECR repositoryyn tagilla "qa"
```
docker build -t asset-history-image .
docker run asset-history-image
aws ecr get-login-password --region [AWS_REGION] | docker login --username AWS --password-stdin [AWS_ACCOUNT_ID].dkr.ecr.[AWS_REGION].amazonaws.com
docker tag asset-history-image [AWS_ACCOUNT_ID].dkr.ecr.[AWS_REGION].amazonaws.com/qa-digiroad2-asset-history-processor-lambda:qa
docker push [AWS_ACCOUNT_ID].dkr.ecr.[AWS_REGION].amazonaws.com/qa-digiroad2-asset-history-processor-lambda:qa
```

### Luo tarvittavat resurssit
```
aws cloudformation create-stack \
--stack-name [esim. qa-digiroad2-asset-history-processor] \
--template-body file://aws/cloudformation/lambda-resources.yaml \
--parameters file://aws/qa/lambda-resources.json \
--tags file://aws/qa/tags.json \
--capabilities CAPABILITY_NAMED_IAM
```

### Luo testi pipeline
*Huom.* Korvaa GitHubWebhookSecret oikealla arvolla
```
aws cloudformation create-stack \
--stack-name [esim. qa-digiroad2-asset-history-pipeline] \
--template-body file://aws/cloudformation/cicd/cicd-stack.yaml \
--parameters file://aws/qa/cicd-parameter.json \
--tags file://aws/qa/tags.json \
--capabilities CAPABILITY_NAMED_IAM
```


# Testiympäristön päivitys

## AWS CLI komennot

*HUOM.* Tarkista ennen jokaista update-stack komentoa parametritiedostojen sisältö.

### Päivitä testi pipeline
```
aws cloudformation update-stack \
--stack-name [esim. qa-digiroad2-asset-history-pipeline] \
--template-body file://aws/cloudformation/cicd/cicd-stack.yaml \
--parameters file://aws/qa/cicd-parameter.json \
--tags file://aws/qa/tags.json \
--capabilities CAPABILITY_NAMED_IAM
```

### Päivitä resurssit
*Huom.* Mikäli myös lambdan koodi halutaan päivittää, korvaa parametrit sisältävän tiedoston parametri "ECRImageTag" uudella ECRImageTag parametrin arvolla.
```
aws cloudformation update-stack \
--stack-name [esim. qa-digiroad2-asset-history-processor] \
--template-body file://aws/cloudformation/lambda-resources.yaml \
--parameters file://aws/qa/lambda-resources.json \
--capabilities CAPABILITY_NAMED_IAM
```
Lisää komentoon mukaan *--tags file://aws/qa/tags.json* mikäli halutaan päivittää myös tagit.
