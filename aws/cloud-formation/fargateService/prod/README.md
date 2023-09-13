# Digiroad tuotanto, pystytys
## VPC
Tarkista, että tuotantotilille on luotu VPC kahdella subnetillä.
Tarkista yhtenevät parametrien nimet, esim. NetworkStackName VPC:n ja CloudFormation parametreistä.

## Kloonaa repo koneellesi
Kloonaa digiroad2-repo omalle koneellesi:

```
git clone https://github.com/finnishtransportagency/digiroad2.git
cd digiroad2
```
## Aseta ympäristömuuttujat
Huom. ympäristömuuttujat säilyvät vain shell / cmd session ajan

*Windows Command Prompt*
```
setx AWS_DEFAULT_REGION eu-west-1
setx AWS_PROFILE centralized_service_admin
```

*Linux / macOS*
```
export AWS_DEFAULT_REGION=eu-west-1
export AWS_PROFILE=centralized_service_admin
```
## AWS CLI komennot

**HUOM tarkista ennen jokaista create-stack komentoa parametritiedostojen sisältö**

### Luo parametrit Parameter Storeen
Parametrit luodaan tyypillä "String" ja arvolla "placeHolderValue"
```
aws cloudformation create-stack \
--stack-name [esim. digiroad-prod-parameter-store-entries] \
--template-body file://aws/cloud-formation/parameter-store/digiroad2-parameter-store.yaml
```
### Päivitä parametrien arvot ja tyypit oikein
Kunkin parametrin tyypiksi vaihdetaan "SecureString" ja arvoksi asetetaan parametrin oikea arvo
Päivitykseen käytettävät komennot löytyvät prod-update-parameter.sh tiedostosta
file://aws/cloud-formation/parameter-store/prod-update-parameter.sh

### Luo ECR repository
```
aws cloudformation create-stack \
--stack-name [esim. digiroad-prod-ecr-repository] \
--template-body file://aws/cloud-formation/ecr/PROD-ECR.yaml \
--parameters file://aws/cloud-formation/ecr/PROD-ECR-parameter.json
```
Repositoryn luonnin jälkeen pyydä kehitystiimiä toimittamaan sinne palvelun image

### Luo Elastic Cache
```
aws cloudformation create-stack \
--stack-name [esim. digiroad-prod-elastic-cache] \
--template-body file://aws/cloud-formation/cache/cache.yaml \
--parameters file://aws/cloud-formation/cache/PROD-cache-parameter.json
```

Ota uuden cachen endpoint osoite ilman porttia, muodossa "clustername.placeholder.cfg.euw1.cache.amazonaws.com"
Talleta endpoint tiedostoon file://aws/cloud-formation/task-definition/prod-taskdefinition-parameter.json

### Luo S3 sekä task definition task role

```
aws cloudformation create-stack \
--stack-name [esim. digiroad-prod-api-s3] \
--capabilities CAPABILITY_NAMED_IAM \
--template-body file://aws/cloud-formation/s3/digiroad2-s3.yaml \
--parameters file://aws/cloud-formation/s3/PROD-s3-parameter.json
```

### Luo task-definition

```
aws cloudformation create-stack \
--stack-name [esim. digiroad-prod-taskdefinition] \
--capabilities CAPABILITY_NAMED_IAM \
--template-body file://aws/cloud-formation/task-definition/prod-create-taskdefinition.yaml \
--parameters file://aws/cloud-formation/task-definition/prod-taskdefinition-parameter.json
```

### Luo Digiroad ALB ja ECS ympäristö
```
aws cloudformation create-stack \
--stack-name [esim. digiroad-ALB-ECS] \
--template-body file://aws/cloud-formation/fargateService/alb_ecs.yaml \
--parameters file://aws/cloud-formation/fargateService/prod/PROD-alb-ecs-parameter.json
```

### Luo SNS-ilmoitukset
```
aws cloudformation create-stack \
--stack-name SNS-notifications \
--template-body file://aws/cloud-formation/sns/snsNotifications.yaml \
--parameters file://aws/cloud-formation/sns/prod-sns-parameter.json
```

##Eräajoja varten tuotantotilille luotavat resurssit

### Luo S3 Bucket lambdan koodia varten
```
aws cloudformation create-stack \
--stack-name [esim. digiroad-batch-lambda-bucket] \
--template-body file://aws/cloud-formation/batchSystem/batchLambda/cicd/prodBatchLambdaDeploymentBucket.yaml \
--parameters file://aws/cloud-formation/batchSystem/batchLambda/cicd/prod-deployment-bucket-parameter.json
```
S3-Bucketin luonnin jälkeen pyydä kehitystiimiä toimittamaan lambdan koodi .zip tiedostona sinne

### Luo Lambda 
```
aws cloudformation create-stack \
--stack-name digiroad-batch-lambda-stack \
--template-body file://aws/cloud-formation/batchSystem/batchLambda/batchLambda.yaml \
--parameters file://aws/cloud-formation/batchSystem/batchLambda/prod-batch-lambda-parameter.json
```

### Luo JobDefinition tuotantoeräajoja varten
```
aws batch register-job-definition \
--profile vaylaapp \
--region eu-west-1 \
--cli-input-json file://aws/cloud-formation/batchSystem/ProdBatchJobDefinition.json
```
### Luo eräajoympäristö
```
aws cloudformation create-stack \
--stack-name [esim. digiroad-batch-system] \
--template-body file://aws/cloud-formation/batchSystem/batchSystem.yaml \
--parameters file://aws/cloud-formation/batchSystem/prod-batch-system-parameter.json
```

# Ympäristön päivitys

**HUOM tarkista ennen jokaista update-stack komentoa parametritiedostojen sisältö**

## Aseta ympäristömuuttujat
Huom. ympäristömuuttujat säilyvät vain shell / cmd session ajan

*Windows Command Prompt*
```
setx AWS_DEFAULT_REGION eu-west-1
setx AWS_PROFILE centralized_service_admin
```

*Linux / macOS*
```
export AWS_DEFAULT_REGION=eu-west-1
export AWS_PROFILE=centralized_service_admin
```
### Task definitionin päivitys
Luo uusi task definition versio
```
aws cloudformation update-stack \
--stack-name [esim. digiroad-prod-taskdefinition] \
--capabilities CAPABILITY_NAMED_IAM \
--template-body file://aws/cloud-formation/task-definition/prod-create-taskdefinition.yaml \
--parameters file://aws/cloud-formation/task-definition/prod-taskdefinition-parameter.json
```

### Uuden task definitionin sekä imagen deploy

```
aws ecs update-service \
--cluster prod-digiroad2-ECS-Cluster-Private \
--service prod-digiroad2-ECS-Service-Private \
--task-definition digiroad2-prod[:VERSION] \
--force-new-deployment
```

### ALB-stackin päivitys
```
aws cloudformation update-stack \
--stack-name [esim. digiroad-ALB-ECS] \
--template-body file://aws/cloud-formation/fargateService/alb_ecs.yaml \
--parameters file://aws/cloud-formation/fargateService/prod/PROD-alb-ecs-parameter.json
```

### JobDefinition päivitys
```
aws batch register-job-definition \
--profile vaylaapp \
--region eu-west-1 \
--cli-input-json file://aws/cloud-formation/batchSystem/ProdBatchJobDefinition.json
```


### Batch-Lambdan koodin päivitys
```
aws lambda update-function-code \
-- function-name Batch-Add-Jobs-To-Queue-New \
-- s3-bucket prod-batch-lambda-deployment-bucket \
-- s3-key deployment_package.zip
```

### Batch-Lambdan päivitys (tarvittaessa)
```
aws cloudformation update-stack \
--stack-name digiroad-batch-lambda-stack \
--template-body file://aws/cloud-formation/batchSystem/batchLambda/batchLambda.yaml \
--parameters file://aws/cloud-formation/batchSystem/batchLambda/prod-batch-lambda-parameter.json \
--capabilities CAPABILITY_NAMED_IAM
```

### S3 ja task definition task role päivitys (tarvittaessa)

```
aws cloudformation update-stack \
--stack-name [esim. digiroad-prod-api-s3] \
--capabilities CAPABILITY_NAMED_IAM \
--template-body file://aws/cloud-formation/s3/digiroad2-s3.yaml \
--parameters file://aws/cloud-formation/s3/PROD-s3-parameter.json
```

## Vanhan imagen laittaminen takaisin
Muokkaa aws/cloud-formation/task-definition/prod-create-taskdefinition.yaml ContainerDefinitions kohtaa Image. Vaihda :prod -> @digest siihen docker digest(esim sha256:b1ff5c8586) jonka kehitystiimi on toimittanut. Konaisuudessa Image kohdassa kuuluisi olla !Sub '${RepositoryURL}@sha256:b1ff5c8586esimerkki'.
Luo uusi task definition versio tästä:
```
aws cloudformation update-stack \
--stack-name [esim. digiroad-prod-taskdefinition] \
--capabilities CAPABILITY_NAMED_IAM \
--template-body file://aws/cloud-formation/task-definition/prod-create-taskdefinition.yaml \
--parameters file://aws/cloud-formation/task-definition/prod-taskdefinition-parameter.json
```
Päivitä palvelu:
```
aws ecs update-service \
--cluster prod-digiroad2-ECS-Cluster-Private \
--service prod-digiroad2-ECS-Service-Private \
--task-definition digiroad2-prod[:VERSION] \
--force-new-deployment
```

Sitten kun kehitystiimi ilmoittaa haluavansa palata normaaliin systeemiin muuta aws/cloud-formation/task-definition/prod-create-taskdefinition.yaml ContainerDefinitions kohtaa Image. Vaihda @digest -> :prod . Konaisuudessa Image kohdassa kuuluisi olla !Sub '${RepositoryURL}:prod'

Luo uusi task definition versio tästä:
```
aws cloudformation update-stack \
--stack-name [esim. digiroad-prod-taskdefinition] \
--capabilities CAPABILITY_NAMED_IAM \
--template-body file://aws/cloud-formation/task-definition/prod-create-taskdefinition.yaml \
--parameters file://aws/cloud-formation/task-definition/prod-taskdefinition-parameter.json
```
Päivitä palvelu:
```
aws ecs update-service \
--cluster prod-digiroad2-ECS-Cluster-Private \
--service prod-digiroad2-ECS-Service-Private \
--task-definition digiroad2-prod[:VERSION] \
--force-new-deployment
```

JobDefinition kohdalla muokkaa aws/cloud-formation/batchSystem/ProdBatchJobDefinition.json containerProperties kohtaa image. Vaihda "920408837790.dkr.ecr.eu-west-1.amazonaws.com/digiroad2:prod" -> "920408837790.dkr.ecr.eu-west-1.amazonaws.com/digiroad2@sha256:b1ff5c8586esimerkki"
siihen docker digest jonka kehitystiimi on toimittanut.

```
aws batch register-job-definition \
--profile vaylaapp \
--region eu-west-1 \
--cli-input-json file://aws/cloud-formation/batchSystem/ProdBatchJobDefinition.json
```

Sitten kun kehitystiimi ilmoittaa haluavansa palata normaaliin systeemiin muuta aws/cloud-formation/batchSystem/ProdBatchJobDefinition.json containerProperties kohtaa image. Vaihda "920408837790.dkr.ecr.eu-west-1.amazonaws.com/digiroad2@sha256:b1ff5c8586esimerkki" -> "920408837790.dkr.ecr.eu-west-1.amazonaws.com/digiroad2:prod"

```
aws batch register-job-definition \
--profile vaylaapp \
--region eu-west-1 \
--cli-input-json file://aws/cloud-formation/batchSystem/ProdBatchJobDefinition.json
```