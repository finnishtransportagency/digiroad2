Käyttöönotto
============

Ympäristön käyttöönotto on automatisoitu [Capistranolla](http://capistranorb.com/).

Käyttöönotto on määritelty `deploy.rb` tiedostossa. Kyseisellä Capistrano-määritelmällä voidaan Digiroad2-järjestelmä asentaa haluttuun ympäristöön.

Liikennevirastolla käyttöönotto on määritelty seuraaville ympäristöille:
* Tuotantoympäristö, jonka asetukset on määritelty `production.rb`- ja `production2.rb`-tiedostoissa.
* Testiympäristö, jonka asetukset on määritelty `staging.rb`-tiedostossa.
* Koulutusympäristö, jonka asetukset on määritelty `training.rb`-tiedostossa.
* Integraatiotestausympäristö, jonka asetukset on määritelty `testing.rb`- ja `testing2.rb`-tiedostoissa.

## Ympäristöjen osoitteiden konfigurointi

Capistrano-skripteissä ei ole konfiguroitu ympäristöjen IP-osoitteita. IP-osoitteet määritellään SSH-konfiguraatiotiedostossa `~/.ssh/config`. Esimerkiksi CI-koneella on konfiguroitu tuotantoympäristön koneet: 

```
host production1
  Hostname <ip-osoite>
  
host production2
  Hostname <ip-osoite>
```

Koneiden host-nimet voi katsoa Capistrano-skripteistä.

## Oracle-kantojen alustus ja päivitys

Käyttöönotto alustaa tarvittaessa Oracle-tietokannan ja skeemat. Tietokannan alustuksessa tietokantayhteyden määritykseen käytetään samaa `bonecp.properties` tiedostoa kuin järjestelmän ajossa. Katso lisätietoja [Digiroad-2](README.md) artikkelista.

Digiroad2:n Oracle-kanta on versioitu. Käyttöönotossa tarkistetaan onko käyttöönotettava järjestelmä riippuva uudemmasta kantaversiosta kuin käytössä oleva. Mikäli kanta on vanhempaa versiota viedään kanta automaattisesti uusimpaan versioon käyttäen tietokantamigraatiomäärityksiä jotka on tallennettu `db.migration` paketissa `digiroad2-oracle` projektissa.

Tietokannan versio ylläpidetään tietokannassa itsessään taulussa `schema_version`.

Tietokannan automaattinen päivitys on toteutettu [Flyway](http://flywaydb.org/) tietokantamigraatiotyökalulla.
