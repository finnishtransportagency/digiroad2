# Kohteiden historiatietojen tallentaja

Käsittelee jonoon (SQS) saapuneet muutostiedot ja tallentaa ne kantaan. Muutostietoja tulee hyödyntämään muun muassa TN-ITS rajapinta.

## Muutokset ja Git branchit

| Ympäristö | Branch                    |
|-----------|---------------------------|
| dev       | dev-assetHistoryProcessor |
| qa        | qa-assetHistoryProcessor  |
| prod      | master                    |

## Lambdojen testaaminen lokaalisti

Lamdojen testaamiseen lokaalisti hyödynnetään [AWS SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html) komentorivityökalua.

Testaamisessa hyödynnetään dev ympäristön resursseja (s3, roolit yms). Jotkin tuotannossa käytössä olevat lambda eventit on otettu käsin pois käytöstä dev ympäristöstä testauksen helpottamiseksi.

### Testaamisessa käytettävät tiedostot

- ./aws/sam/template.yaml Funktion testaamiseen käytettävä SAM template
- "events" kansiosta löytyy testi eventtejä lambdalle.

### Testaaminen lambdan omalla roolilla

Jotta lambdaa voitaisiin testata oikeilla pääsyoikeuksilla, tulee koneen .aws/config tiedostoon lisätä tiedot lambdan roolille, jotta sitä voidaan käyttää testaamisessa.

```sh
[profile AssetHistoryLambdaRole]
role_arn = arn:aws:iam::ACCOUNT_ID:role/LAMBDA_ROLE_NAME
source_profile = vaylaapp
output = json
region = REGION
```

- account_id Korvaa oikealla AWS account id:llä
- lambda_role_name Korvaa oikealla funktion dev ympäristön roolilla.
- region Korvaa oikealla AWS regionilla

### Lokaali debug

Koska lambda on yhteydessä Digiroadin kantaan, vaatii lokaali testaaminen yhteyden kantaan.

#### Yhteys lokaaliin Digiroad kantaan
Tarkemmat ohjeet: aws/local-dev/postgis/README.md
```sh
cd ..\..\aws\local-dev\postgis
docker compose up -d
cd ..\..\..\lambda\asset-history-processor
```

```sh
sam build -t aws/sam/template.yaml --region REGION
sam local invoke Lambda --profile AssetHistoryLambdaRole --region REGION --event events/test.json --log-file debug.log
```

- "sam build" muodostaa container imagen. Ajetaan aina muutoksien jälkeen.
- "sam local invoke" käynnistää lokaalin lambda funktion annetuilla parametreilla
