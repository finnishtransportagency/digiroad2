Digiroad2
=========

Ympäristön pystytys
===================

1. Kloonaa digiroad2-repo omalle koneellesi

  ```
  git clone https://github.com/finnishtransportagency/digi-road-2.git
  ```

2. [Asenna node.js](http://howtonode.org/how-to-install-nodejs) (samalla asentuu [npm](https://npmjs.org/))


3. Hae ja asenna projektin tarvitsemat riippuvuudet hakemistoon, johon projekti on kloonattu

  ```
  npm install
  ```

4. Asenna [grunt](http://gruntjs.com/getting-started)

  ```
  npm install -g grunt-cli
  ```

Lisää Configuration jos käytät intellij
----------------

Luo .idea kansioon runConfigurations kansio. Kopioi aws/local-dev/idea-run-configurations konfiguraatiot
.idea/runConfigurations kansioon. Käynistä IDE uudestaan. Server configuraatiossa ota "use sbt shell" ruksi pois edit configuration.

digiroad2-oracle
----------------

digiroad2-oracle moduuli toteuttaa oracle-spesifisen tuen digiroad2:n `AssetProvider` ja `UserProvider` - rajapinnoista.
Moduuli tuottaa kirjaston, joka lisätään ajonaikaisesti digiroad2-sovelluksen polkuun.

Lokaali tietokannan alustus
----------------

Backend palvelimen käynistäminen edellyttää, että paikallinen tietokanta on päälä ja alustettu.

Laita aws/local-dev/postgis/docker-compose.yaml pääle ja luo ympäristöt unittest ja lokaaliin kehitykseen.

docker-compose -p "localtest" -f .\aws\local-dev\postgis\docker-compose.yaml create
docker-compose -p "unittest" -f .\aws\local-dev\postgis\docker-compose.yaml create

Käynnistä joko terminaalissa tai Docker Desktop.
docker-compose -p "localtest" -f .\aws\local-dev\postgis\docker-compose.yaml start
docker-compose -p "unittest" -f .\aws\local-dev\postgis\docker-compose.yaml start

Alusta kanta ajamalla DataFixture init configuraatio. Sitten aja DataFixture reset tai migrate configuraatio.

Ajaminen
========

Buildin rakentaminen:
```
grunt
```

Testien ajaminen:
```
grunt test
```

Kehitysserverin pystytys:
Aseta ympäristömuuttuja rasterService_apikey=apiavain . 
Parametri voidaan asettaa Intellij Grunt Configuration Environment.
```
grunt server
```
Kehitysserveri ajaa automaattisesti testit, kääntää lessit ja toimii watch -tilassa.

Kehityspalvelin ohjaa API-kutsut API-palvelimelle. Jotta järjestelmä toimii tulee myös API-palvelimen olla käynnissä.

API-palvelin
============

API-palvelimen buildia käsitellään sbt:llä, käyttäen projektin juuressa olevaa launcher-skriptiä sbt. Esim.

```
./sbt test
```

Palvelimen voi käynnistää ajamalla Server configuration myös.

API-palvelimen saa käyntiin kehitysmoodiin seuraavalla sbt komennolla:
```
./sbt '~;container:start; container:reload /'
```

Ympäristömuuttuja parametri voidaan asettaa Intellij SBT Configuration Environment Variable avulla.
Nämä voidaan syöttään myös ympäristömuuttujina:
```
viiteRestApiEndPoint=url
viite.apikey=insertapikey
vkm.apikey=insertapikey 
Doag.username=svc_clouddigiroad
oag.password=svc_clouddigiroad 
rasterService.apikey=insertapikey
googlemapapi.client_id=XYZ123
googlemapapi.crypto_key=ZYX321
ses.username=sesusername
ses.password=sespassword
bonecp.jdbcUrl=kantaurl
bonecp.username=kantakäyttäjä
bonecp.password=kantasalasana
vvhRest.password=insertpassword
```
Windowsissa toimii komento:
```
run fi.liikennevirasto.digiroad2.ProductionServer
```

Avaa käyttöliittymä osoitteessa <http://localhost:9001/login.html>.
Kirjaudut sisään käyttäen lokaalia testi käyttäjää nimeltään silari.


Tielinkiverkon lataaminen.
======================================================

Jotta skripti toimisi PostgreSql pitää olla asennettu ja C:\'Program Files'\PostgreSQL\13\bin\ pitää olla lisätty path env.

Avaa ssh yhteys bastion koneeseen ja ohjaa tietokanta johonkin lokaaliin porttiin. Tarvittavat ohjeet löytyy wiki sivulta https://extranet.vayla.fi/wiki/display/DROTH/AWS+Ohjeita
Ajo skripti projektin juuressa.
```
 .\importRoadlink.ps1 
 -municipalities "20,10" # kunnat jotka haluat tuoda
 -sourceUser digiroad2dbuser 
 -sourcePasstword password 
 -sourceDB digiroad2 
 -sourcePort 9999 
 -destinationPastword digiroad2
 -truncateBoolean 1 # 1 tyhjennetään taulu ennen kuin tuodaan uudet linkit, 0 kantaa ei tyhjennetä
```

Käyttäjien lisääminen ja päivittäminen CSV-tiedostosta
======================================================

Palvelun käyttäjien tietoja voi päivittää ja uusia käyttäjiä voi lisätä CSV - tiedostosta, jossa on määritelty uusien ja päivitettävien käyttäjien käyttäjänimet sekä kuntatunnukset joihin näillä käyttäjillä tulisi olla oikeudet.

Alla esimerkki CSV-tiedostosta:
```
kuntakäyttäjä; ;105, 258, 248, 245;
olemassaolevatunnus; ;410, 411, 412, 413;
elykäyttäjä;0,1,2,3,4,5,6,7,8,9;
```
