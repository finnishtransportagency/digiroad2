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

Laita aws/local-dev/postgis/docker-compose.yaml pääle.

Alusta kanta ajamalla DataFixture init configuraatio. Sitten aja DataFixture reset configuraatio.

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

Esim CI-ympäristössä Oracle-tietokanta ei ole käytettävissä, jolloin buildille pitää välittää system property "digiroad2.nodatabase" arvolla "true".
Vastaavasti buildille voi välittää kohteena oleva ympäristö propertyllä "digiroad2.env" (arvot "dev", "test", "prod" tai "ci"). Esim.

```
./sbt -Ddigiroad2.nodatabase=true -Ddigiroad2.env=dev test
```

"digiroad2.env":n arvo määrittää sen, minkä ympäristön konfiguraatiotiedostot otetaan käyttöön (hakemistosta conf/(env)/)

Windowsissa toimii komento:
```
run fi.liikennevirasto.digiroad2.ProductionServer
```

Avaa käyttöliittymä osoitteessa <http://localhost:9001/login.html>.
Kirjaudut sisään käyttäen lokaalia testi käyttäjää silari.

Käyttäjien lisääminen ja päivittäminen CSV-tiedostosta
======================================================

Palvelun käyttäjien tietoja voi päivittää ja uusia käyttäjiä voi lisätä CSV - tiedostosta, jossa on määritelty uusien ja päivitettävien käyttäjien käyttäjänimet sekä kuntatunnukset joihin näillä käyttäjillä tulisi olla oikeudet.

Alla esimerkki CSV-tiedostosta:
```
kuntakäyttäjä; ;105, 258, 248, 245;
olemassaolevatunnus; ;410, 411, 412, 413;
elykäyttäjä;0,1,2,3,4,5,6,7,8,9;
```

Käyttäjiä voi päivittää ja lisätä käyttäen `import-users-from-csv.sh` skriptiä:
```
./import-users-from-csv.sh <digiroad2-palvelin:portti> <ylläpitäjän-tunnus> <polku-csv-tiedostoon>
```

[Käyttöönotto ja version päivitys](Deployment.md)
=================================================
