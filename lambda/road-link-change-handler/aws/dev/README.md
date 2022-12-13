# Kehitysympäristön pystytys

## Siirry Digiroad projektin juuresta lambda funktion omaan kansioon
```
cd lambda/road-link-change-handler
```

## AWS CLI komennot

*HUOM.* Tarkista ennen jokaista create-stack komentoa parametritiedostojen sisältö.

### Luo ECR repository
```
aws cloudformation create-stack \
--stack-name [esim. dev-digiroad2-road-link-change-lambda-ecr] \
--template-body file://aws/cloudformation/ecr/ecr.yaml \
--parameters ParameterKey=Environment,ParameterValue=dev \
--tags file://aws/dev/tags.json
```

### Vie ensimmäinen palvelun image uuteen ECR repositoryyn tagilla "latest"
```
docker build -t image .
docker run image
aws ecr get-login-password --region [AWS_REGION] | docker login --username AWS --password-stdin [AWS_ACCOUNT_ID].dkr.ecr.[AWS_REGION].amazonaws.com
docker tag image [AWS_ACCOUNT_ID].dkr.ecr.[AWS_REGION].amazonaws.com/digiroad2-road-link-change-lambda:latest
docker push [AWS_ACCOUNT_ID].dkr.ecr.[AWS_REGION].amazonaws.com/digiroad2-road-link-change-lambda:latest
```

### Luo tarvittavat resurssit
```
aws cloudformation create-stack \
--stack-name [esim. dev-digiroad2-road-link-change-handler] \
--template-body file://aws/cloudformation/lambda-resources.yaml \
--parameters file://aws/dev/lambda-resources.json \
--tags file://aws/dev/tags.json \
--capabilities CAPABILITY_NAMED_IAM
```

### Laita lambdan event ajastus pois päältä
Disabloi lambdan käynnistävä EventBridge sääntö.
*Huom.* Varmista että eventin nimi vastaa lambda-resources.yaml:lla luotua
```
aws events disable-rule --name dev-digiroad2-start-road-link-change-handler-event
```

### Laita lambdan event ajastus päälle
Laita lambdan käynnistävä EventBridge sääntö takaisin päälle siinä vaiheessa, kun lambdan toteutus on valmis.
*Huom.* Varmista että eventin nimi vastaa lambda-resources.yaml:lla luotua
```
aws events enable-rule --name dev-digiroad2-start-road-link-change-handler-event
```


# Kehitysympäristön päivitys

## AWS CLI komennot

*HUOM.* Tarkista ennen jokaista update-stack komentoa parametritiedostojen sisältö.

### Päivitä resurssit
*Huom.* Korvaa parametrit sisältävän tiedoston parametri "ECRImageTag" uudella ECRImageTag parametrin arvolla jos lambdan koodissa on tapahtunut muutoksia.
```
aws cloudformation update-stack \
--stack-name [esim. dev-digiroad2-road-link-change-handler] \
--template-body file://aws/cloudformation/lambda-resources.yaml \
--parameters file://aws/dev/lambda-resources.json \
--capabilities CAPABILITY_NAMED_IAM
```
Lisää komentoon mukaan *--tags file://aws/dev/tags.json* mikäli halutaan päivittää myös tagit.
