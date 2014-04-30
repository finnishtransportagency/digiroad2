Operaattorin manuaali Digiroad 2 -sovellukseen
----------------------------------------------

1. Uuden käyttäjän lisääminen
-----------------------------

Vain operaattori-käyttäjä voi lisätä uuden käyttäjän. Uusi käyttäjä lisätään osoitteessa:

https://testiextranet.liikennevirasto.fi/digiroad/newuser.html 

Käyttöliittymässä on lomake, johon tulee täydentää seuraavat tiedot:

1. Käyttäjätunnus: Käyttäjän tunnus Liikenneviraston järjestelmiin
1. Ely nro: ELY:n numero tai pilkulla erotettuna useamman ELY:n numerot (esimerkiksi 1, 2, 3)
1. Kunta nro: Kunnan numero tai pilkulla erotettuna useamman kunnan numerot (esimerkiksi 091, 092)
1. Oikeuden tyyppi: Muokkausoikeus tai Katseluoikeus

Kun lomake on täytetty, painetaan "Luo käyttäjä". Sovellus ilmoittaa onnistuneesta käyttäjän lisäämisestä. Jos käyttäjäksi lisätään jo olemassa olevan käyttäjän, sovellus poistaa vanhan ja korvaa sen uudella käyttäjällä.

![Käyttäjän lisääminen](k20.JPG)

_Uuden käyttäjän lisääminen._

2. Pysäkkitietojen vienti Vallu-järjestelmään
---------------------------------------------

Järjestelmä tukee pysäkkitietojen vientiä Vallu-järjestelmään. Pysäkkitiedot toimitetaan .csv-tiedostona FTP-palvelimelle. Vienti käynnistetään ajamalla 'vallu_import.sh' skripti. Skripti hakee pysäkkitiedot tietokannasta käyttäen projektille määriteltyä kohdetieto-tietolähdettä.

FTP-yhteys ja kohdekansio tulee määritellä 'ftp.conf'-tiedostossa joka on tallennettu samaan 'vallu_import.sh' skriptin kanssa. 'ftp.conf'-tiedostossa yhteys ja kohdekansio määritellään seuraavalla tavalla:
```
<käyttäjänimi> <salasana> <palvelin ja kohdehakemisto>
```

Esimerkiksi:
```
username password localhost/valluexport
```

Vienti luo FTP-palvelimelle pysäkkitiedot zip-pakattuna .csv-tiedostona nimellä 'digiroad_stops.zip' sekä 'flag.txt'-tiedoston, joka sisältää Vallu-viennin aikaleiman muodossa vuosi (4 merkkiä), kuukausi (2 merkkiä), päivä (2 merkkiä), tunti (2 merkkiä), minuutti (2 merkkiä), sekunti (2 merkkiä). Esimerkiksi '20140417133227'.

Käyttöönotto kopioi ympäristökohtaisen 'ftp.conf'-tiedoston käyttöönottoympäristön deployment-hakemistosta release-hakemistoon osana käyttöönottoa. Näin ympäristökohtaista 'ftp.conf'-tiedostoa, joka sisältää kirjautumistietoja, voidaan ylläpitää tietoturvallisesti käyttöönottopalvelimella. 