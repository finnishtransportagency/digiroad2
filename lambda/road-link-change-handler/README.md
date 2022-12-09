# Tielinkkien muutosten kyselijä lambda

Hakee tielinkkiverkolla tapahtuneet muutokset tietyllä aikavälillä, tallentaa uudet linkit kantaan sekä merkitsee poistuvat linkit poistuviksi. Tallentaa lopuksi muutoksien tiedot muutossettinä S3:een.

## Muutokset ja Git branchit

| Ympäristö | Branch                          |
|-----------|---------------------------------|
| dev       | dev-lambdaRoadLinkChangeHandler |
| qa        | qa-lambdaRoadLinkChangeHandler  |
| prod      | master                          |

## Lambdojen testaaminen lokaalisti

Lamdojen testaamiseen lokaalisti hyödynnetään [AWS SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html) komentorivityökalua.

Testaamisessa hyödynnetään dev ympäristön resursseja (s3, roolit yms).

### Testaamisessa käytettävät tiedostot

- ./aws/sam/template.yaml Funktion testaamiseen käytettävä SAM template

### Testaaminen lambdan omalla roolilla

Jotta lambdaa voitaisiin testata oikeilla pääsyoikeuksilla, tulee koneen .aws/config tiedostoon lisätä tiedot lambdan roolille, jotta sitä voidaan käyttää testaamisessa.

```sh
[profile RoadLinkChangeLambdaRole]
role_arn = arn:aws:iam::ACCOUNT_ID:role/LAMBDA_ROLE_NAME
source_profile = vaylaapp
output = json
region = REGION
```

- account_id Korvaa oikealla AWS account id:llä
- lambda_role_name Korvaa oikealla funktion dev ympäristön roolilla.
- region Korvaa oikealla AWS regionilla

### Lokaali debug

```sh
sam build -t aws/sam/template.yaml --region REGION
sam local invoke Lambda --profile RoadLinkChangeLambdaRole --region REGION --env-vars env.json --log-file debug.log
```

- "sam build" muodostaa container imagen. Ajetaan aina muutoksien jälkeen.
- "sam local invoke" käynnistää lokaalin lambda funktion annetuilla parametreilla
