# Digiroad tuotanto, pystytys
## VPC
Luo VPC AWS-pilveen kahdella subnetilla.
Tarkista yhtenevät parametrien nimet, esim. NetworkStackName VPC:n ja CloudFormation parametreistä.

## Kloona repo koneellesi
Kloonaa digiroad2-repo omalle koneellesi ja tee haaranvaihto AwsPostGist -haaraan

```
git clone https://github.com/finnishtransportagency/digiroad2.git
cd digiroad2
git checkout origin/AwsPostGist
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

### Luo parametrit parameterStoreen
Parametrit luodaan tyypillä "String" ja arvolla "placeHolderValue"
```
aws cloudformation create-stack \
--stack-name [esim. digiroad-prod-parameter-store-entries] \
--template-body file://aws/cloud-formation/fargateService/prod/PROD-alb-ecs-parameter.json
```
### Päivitä parametrien arvot ja tyypit oikein
Kunkin parametrin tyypiksi vaihdetaan "SecureString" ja arvoksi asetetaan parametrin oikea arvo
Päivitykseen käytettävät komennot löytyvät prod-update-parameter.sh tiedostosta

### Luo ECR repository
```
aws cloudformation create-stack \
--stack-name [esim. digiroad-prod-ecr-repository] \
--template-body file://aws\cloud-formation\ecr\PROD-ECR.yaml \
--parameters file://aws/cloud-formation/fargateService/prod/PROD-alb-ecs-parameter.json
```
Repositoryn luonnin jälkeen pyydä kehitystiimiä toimittamaan sinne palvelun image

### Luo task-definition

```
aws cloudformation create-stack \
--stack-name [esim. digiroad-prod-taskdefinition] \
--capabilities CAPABILITY_NAMED_IAM \
--template-body file://aws/cloud-formation/task-definition/prod-create-taskdefiniton.yaml \
--parameters ParameterKey=RepositoryURL,ParameterValue=[URL äsken luotuun ECR repositoryyn jossa kontti sijaitsee esim. 012345678910.dkr.ecr.eu-west-1.amazonaws.com/digiroad2]
```

### Luo Digiroad ALB ja ECS ympäristö
```
aws cloudformation create-stack \
--stack-name [esim. digiroad-ALB-ECS] \
--on-failure DELETE \
--template-body file://aws/cloud-formation/fargateService/alb_ecs.yaml \
--parameters file://aws/cloud-formation/fargateService/prod/PROD-alb-ecs-parameter.json
```

# Ympäristön päivitys

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
--template-body file://aws/cloud-formation/task-definition/prod-create-taskdefiniton.yaml \
--parameters ParameterKey=RepositoryURL,ParameterValue=[URL repositoryyn jossa kontti sijaitsee esim. 012345678910.dkr.ecr.eu-west-1.amazonaws.com/digiroad2]
```
Ota juuri luotu task definition versio käyttöön. \
Huom.: [:VERSION] -kohdan pois jättäminen ottaa käyttöön viimeisimmän task definition version ("latest") 
```
aws ecs update-service \
--cluster prod-digiroad2-ECS-Cluster-Private \
--service prod-digiroad2-ECS-Service-Private \
--task-definition Prod-Viite[:VERSION] \
--force-new-deployment
```

### ALB-stackin päivitys
```
aws cloudformation update-stack \
--stack-name [esim. digiroad-ALB-ECS] \
--capabilities CAPABILITY_NAMED_IAM \
--template-body file://aws/cloud-formation/fargateService/alb_ecs.yaml \
--parameters file://aws/cloud-formation/fargateService/prod/PROD-alb-ecs-parameter.json
```
