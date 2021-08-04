Digiroad2
=========

Ympäristön pystytys
===================

1. Kloonaa digiroad2-repo omalle koneellesi

  ```
  git clone https://github.com/finnishtransportagency/digi-road-2.git
  ```

1. [Asenna node.js](http://howtonode.org/how-to-install-nodejs) (samalla asentuu [npm](https://npmjs.org/))
1. Asenna [yarn](https://yarnpkg.com/lang/en/)

  ```
  npm install -g yarn
  ```

1. Hae ja asenna projektin tarvitsemat riippuvuudet hakemistoon, johon projekti on kloonattu

  ```
  npm install && yarn install
  ```

1. Asenna [grunt](http://gruntjs.com/getting-started)

  ```
  npm install -g grunt-cli
  ```

digiroad2-oracle
----------------

digiroad2-oracle moduuli toteuttaa oracle-spesifisen tuen digiroad2:n `AssetProvider` ja `UserProvider` - rajapinnoista.
Moduuli tuottaa kirjaston, joka lisätään ajonaikaisesti digiroad2-sovelluksen polkuun.

Build edellyttää, että paikallinen tietokantaymäristö on alustettu ja konfiguroitu:

Kopioi tiedostot ojdbc6.jar, sdoapi.jar ja sdoutl.jar hakemistoon `digiroad2-oracle/lib`. Tiedostot saa [digiroad2-oracle-projektista](https://github.com/finnishtransportagency/digiroad2-oracle/tree/master/lib).

Luo digiroad2/digiroad2-oracle/conf/dev/bonecp.properties ja lisää sinne tietokantayhteyden tiedot:

```
bonecp.jdbcUrl=jdbc:oracle:thin:@<tietokannan_osoite>:<portti>/<skeeman_nimi>
bonecp.username=<käyttäjätunnus>
bonecp.password=<salasana>
```

Tietokantayhteyden voi määrittää myös ulkoisessa properties tiedostossa joka noudattaa yllä olevaa muotoa.

Tällöin digiroad2/digiroad2-oracle/conf/dev/bonecp.properties tiedosto viittaa ulkoiseen tiedostoon:

```
digiroad2-oracle.externalBoneCPPropertiesFile=/etc/digiroad2/bonecp.properties
```

Tietokanta ja skeema voidaan alustaa käyttäen `fixture-reset.sh` skriptiä.

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
