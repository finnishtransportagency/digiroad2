digi-road-2
===========

[![Build Status] (https://travis-ci.org/finnishtransportagency/digiroad2.png)]
(https://travis-ci.org/finnishtransportagency/digiroad2)

Ympäristön pystytys
===================

1. Kloonaa digiroad2-repo omalle koneellesi

  ```
  git clone https://github.com/finnishtransportagency/digi-road-2.git
  ```

1. [Asenna node.js](http://howtonode.org/how-to-install-nodejs) (samalla asentuu [npm](https://npmjs.org/))
1. Asenna [bower](https://github.com/bower/bower)

  ```
  npm install -g bower
  ```

1. Hae ja asenna projektin tarvitsemat riippuvuudet

  ```
  npm install && bower install
  ```

1. Asenna [grunt](http://gruntjs.com/getting-started)

  ```
  npm install -g grunt-cli
  ```

Tietolähteet käyttäjä- sekä tie- ja kohdetiedoille
==================================================

Digiroad2 käsittelee ja käyttää tie- ja kohdetietoja joihin pääsy on toteutettu `AssetProvider` - rajapinnan kautta.
Pääsy käyttäjätietoihin toteutetaan `UserProvider` - rajapinnan kautta.

Projekti tarjoaa Oraclen tietokantaan integroituvat toteutukset digiroad2-oracle moduulissa.

Tuki vaihtoehtoisille käyttäjä- sekä tie- ja kohdetietokannoille on saavutettavissa toteuttamalla `AssetProvider` ja `UserProvider` - rajapinnat.

digiroad2-oracle
----------------

digiroad2-oracle moduuli toteuttaa oracle-spesifisen tuen digiroad2:n `AssetProvider` ja `UserProvider` - rajapinnoista.
Moduuli tuottaa kirjaston, joka lisätään ajonaikaisesti digiroad2-sovelluksen polkuun.

Build edellyttää, että paikallinen tietokantaymäristö on alustettu ja konfiguroitu:

Luo digiroad2/digiroad2-oracle/conf/dev/bonecp.properties ja lisää sinne tietokantayhteyden tiedot:

```
bonecp.jdbcUrl=jdbc:oracle:thin:@<tietokannan_osoite>:<portti>/<skeeman_nimi>
bonecp.username=<käyttäjätunnus>
bonecp.password=<salasana>
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
