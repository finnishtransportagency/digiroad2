# CLI autentikointi -skripti

## Kuvaus
Skripti ```vaylaAssumeRoleAWSCLI.py``` auttaa käyttäjää autentikoitumaan Väylän pääsynhallintaa vasten ja saamaan AWS CLI:lle väliaikaiset credentiaalit. Näiden avulla voi sitten mm. ajaa AWS CLI-komentoja sekä kehittää AWS SDK:lla ja CDK:lla.

## Käyttöohje

### Esivaatimukset

Työasemallasi pitää olla asennettuna Python 3.X ja ao. kirjastot.

#### Pythonin asennus

Tarkista ensin, että sinulla ei jo ole Python 3.X -asennettuna ajamalla komento
```
python --version
```
Jos tämä palauttaa 2. version, kokeile vielä, olisiko Python 3 asennettu eri nimelle:
```
python3 --version
```
Jos Python 3 löytyy tuolla nimellä, niin korvaa alla kaikki ```python``` -komennot tuolla ```python3``` -komennolla (tai linkitä ```python3 python```:iksi)


Pythonin voi asentaa monella eri tavalla eli esim. menemällä Pythonin virallisille sivuille ja lataamalla asennuspaketti ja asentamalla se: https://www.python.org/downloads/

Windowsille haluat todennäköisesti asentaa sen Windows x86-64 executable installer -nimisestä paketista.

Toinen tapa on käyttää oman käyttöjärjestelmän pakettimanagereja eli esim. Macissä suosittu brew: ```brew install python```

#### Tarvittavien kirjastojen asennus

Kun Python 3 on asennettu pitää vielä asentaa seuraavat kirjastot:
* requests
* bs4
* boto3
* pytz

Tämä tapahtuu komennolla:
```
pip install requests bs4 boto3 pytz
```


### Login jollekin tilille jollekin roolille

Peruskäyttötapa, kun haluataan kredentiaalit jollekin Väyläviraston AWS-tilille, on seuraava:
```
python vaylaAssumeRoleAWSCLI.py --username <Your Väylä username> --account 783354560127 --role ViiteAdmin --region eu-west-1
```
Eli ```--username``` -parametrina annetaan Väyläviraston käyttäjätunnus. ```--account``` -parametrina annetaan AWS-tilin numero, jolle ollaan menossa. ```--role``` -parametrina annetaan AWS-tilin roolin nimi, jolle ollaan menossa.

Kun komento ajetaan, kysyy se ensin Väyläviraston käyttäjätunnuksen salasanan. Seuraavaksi se kysyy SMS:nä tulevan PIN-koodin. Jos käyttäjällä on useampi rooli Väyläviraston AWS IAM-tilillä, kysytään seuraavaksi, mille roolille logataan. Yleisessä tapauksessa on vain yksi rooli, joten sitä ei kysytä.

Komennon onnistuneen ajamisen jälkeen käyttäjän kotihakemistossa sijaitsevassa ```.aws``` -hakemistossa tiedostossa ```credentials``` on kaksi uutta profiilia, joita voi sitten käyttää AWS CLI ymv. komennoissa.

Oletuksena IAM-tilin kredentiaalit ovat voimassa 12h ja sovellustilin kredentiaalit 1h. Nämä ovat samalla maksimit (AWS ei salli pidempiä aikoja).

#### Sovellustilin kredentiaalien virkistys

Sovellustilin kredentiaalit voi virkistää ilman uutta loginia ajamalla komento:
```
python vaylaAssumeRoleAWSCLI.py --refresh true
```

### Parametrit
* ```--help``` tulostaa parametrit ja ohjeet.
* ```--username``` asettaa Väyläviraston käyttäjätunnuksen. Esim. ```LX181335```. Mikäli tätä ei anneta parametrina, se kysytään ajettaessa.
* ```--account``` asettaa AWS-tilin numeron, jolle halutaan kredentiaalit. Esim. ```783354560127```.
* ```--role``` asettaa AWS-tilin roolin nimen, jolle ollaan menossa. Esim. ```ViiteAdmin```.
* ```--output``` asettaa AWS CLI:n output formaatin. Vaihtoehdot ovat ```json,yaml,text,table```. Oletus on ```json```.
* ```--region``` asettaa AWS CLI:n regioonan ko. profiilille. Oletus on ```eu-west-1``` eli Irlanti.
* ```--duration``` asettaa IAM-tilin kredentiaalien keston. Oletus on ```43200``` eli 12h. Tämä on samalla maksimi, jonka AWS hyväksyy.
* ```--refresh``` parametrilla saa päivitettyä sovellustilin kredentiaalit. Jos tämä parametri on annettu, skripti ei tee mitään muuta. Oletuksena se on ```false``` ja pitää asettaa arvoon ```true```, jos haluaa tämän toimivan.
* ```--profile``` asettaa AWS credentials -tiedoston profiilin nimen sovellustilille. Oletuksena tämä on ```vaylaapp```.


### Erikoiskäyttötapauksia

1) Jos haluat vain saada kredentiaalit IAM-tilille, komennon voi ajaa seuraavasti:
    ```
    python vaylaAssumeRoleAWSCLI.py --username LX181335
    ```
2) Jos haluat nimetä credentials -tiedostoon tulevan sovellustilin profiilin joksikin muuksi kuin tuo oletus ```vaylaapp``` käytä ```--profile``` -parametria:
    ```
    python vaylaAssumeRoleAWSCLI.py --username LX181335 --account 783354560127 --role ViiteAdmin --profile appadmin
    ```
    ja sitten voit tunnin jälkeen refreshata ko. kredentiaalin komennolla:
    ```
    python vaylaAssumeRoleAWSCLI.py --refresh true --profile appadmin
    ```

### CodeCommit:in käyttö (Git)
TODO:
https://docs.aws.amazon.com/codecommit/latest/userguide/temporary-access.html
https://pypi.org/project/git-remote-codecommit/

pip install git-remote-codecommit

git clone codecommit://vaylaapp@vayla-oper-cf

### Kehitysideoita & muistiinpanoja tämän skriptin kehittämiseen
* Jos haluaa autentikoinnin jälkeen tehdä toisen assume role:n toiseen tiliin ja/tai rooliin, voisi olla olemassa optio, jolla sen saisi tehtyä
* Asennuspaketti
* Uuden version ilmaisin
* Jos ~/.aws/credentials -tiedostoa ei ole olemassa, tämä skripti kaatuu tällä hetkellä

### Ulkopuolinen dokumentaatio
* [AWS:n boto3 Python-kirjaston dokumentaatio STS:n suhteen](https://boto3.amazonaws.com/v1/documentation/api/latest/reference/services/sts.html)
