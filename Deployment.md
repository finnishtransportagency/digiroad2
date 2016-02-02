# Versiojulkaisu

Ympäristön käyttöönotto ja versioiden julkaisu on automatisoitu [Capistranolla](http://capistranorb.com/). Uuden version julkaisu tehdään `cap`-komennolla. Esimerkiksi testiympäristöön vienti on:

    bundle exec cap staging deploy

Tämän komennon voi ajaa kehittäjän koneella sekä CI-koneella.

Käyttöönotto on määritelty [config/deploy.rb](config/deploy.rb)-tiedostossa. Kyseisellä Capistrano-määritelmällä voidaan Digiroad2-järjestelmä asentaa haluttuun ympäristöön. Lisäksi kullekin kohdeympäristölle määritellään omat tiedostot, kuten alla.

Liikennevirastolla käyttöönotto on määritelty seuraaville ympäristöille:
* Tuotantoympäristö, jonka asetukset on määritelty [production.rb](config/deploy/production.rb)- ja [production2.rb](config/deploy/production2.rb)-tiedostoissa.
* Testiympäristö, jonka asetukset on määritelty [staging.rb](config/deploy/staging.rb)-tiedostossa.
* Koulutusympäristö, jonka asetukset on määritelty [training.rb](config/deploy/training.rb)-tiedostossa.
* Integraatiotestausympäristö, jonka asetukset on määritelty [testing.rb](config/deploy/testing.rb)- ja [testing2.rb](config/deploy/testing2.rb)-tiedostoissa.

## Ympäristöjen osoitteiden asettaminen

Capistrano-skripteissä ei ole asetettu ympäristöjen IP-osoitteita. IP-osoitteet kullekin palvelinnimelle määritellään SSH-asetustiedostossa `~/.ssh/config`. Esimerkiksi CI-koneella on asetettu tuotantoympäristön palvelinnimet: 

```
host production1
  Hostname <ip-osoite>
  
host production2
  Hostname <ip-osoite>
```

Koneiden palvelinnimet voi katsoa Capistrano-skripteistä.

## Oracle-kantojen alustus ja päivitys

Käyttöönotto alustaa tarvittaessa Oracle-tietokannan ja skeemat. Tietokannan alustuksessa tietokantayhteyden määritykseen käytetään samaa `bonecp.properties` tiedostoa kuin järjestelmän ajossa. Katso lisätietoja [Digiroad-2](README.md) artikkelista.

Digiroad2:n Oracle-kanta on versioitu. Käyttöönotossa tarkistetaan onko käyttöönotettava järjestelmä riippuva uudemmasta kantaversiosta kuin käytössä oleva. Mikäli kanta on vanhempaa versiota viedään kanta automaattisesti uusimpaan versioon käyttäen tietokantamigraatiomäärityksiä jotka on tallennettu `db.migration` paketissa `digiroad2-oracle` projektissa.

Tietokannan versio ylläpidetään tietokannassa itsessään taulussa `schema_version`.

Tietokannan automaattinen päivitys on toteutettu [Flyway](http://flywaydb.org/) tietokantamigraatiotyökalulla.
