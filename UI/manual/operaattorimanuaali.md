Operaattorin manuaali Digiroad 2 -sovellukseen
----------------------------------------------

1. Uuden k&auml;ytt&auml;j&auml;n lis&auml;&auml;minen
-----------------------------

Vain operaattori-k&auml;ytt&auml;j&auml; voi lis&auml;t&auml; uuden k&auml;ytt&auml;j&auml;n. Uusi k&auml;ytt&auml;j&auml; lis&auml;t&auml;&auml;n [k&auml;ytt&auml;j&auml;nhallinnassa.](https://testiextranet.liikennevirasto.fi/digiroad/newuser.html )

K&auml;ytt&ouml;liittym&auml;ss&auml; on lomake, johon tulee t&auml;ydent&auml;&auml; seuraavat tiedot:

1. K&auml;ytt&auml;j&auml;tunnus: K&auml;ytt&auml;j&auml;n tunnus Liikenneviraston j&auml;rjestelmiin
1. Ely nro: ELY:n numero tai pilkulla erotettuna useamman ELY:n numerot (esimerkiksi 1, 2, 3)
1. Kunta nro: Kunnan numero tai pilkulla erotettuna useamman kunnan numerot (esimerkiksi 091, 092)
1. Oikeuden tyyppi: Muokkausoikeus tai Katseluoikeus

Kun lomake on t&auml;ytetty, painetaan "Luo k&auml;ytt&auml;j&auml;". Sovellus ilmoittaa onnistuneesta k&auml;ytt&auml;j&auml;n lis&auml;&auml;misest&auml;. Jos k&auml;ytt&auml;j&auml;ksi lis&auml;t&auml;&auml;n jo olemassa olevan k&auml;ytt&auml;j&auml;, sovellus poistaa vanhan ja korvaa sen uudella k&auml;ytt&auml;j&auml;ll&auml;. K&auml;ytt&auml;j&auml;n lis&auml;&auml;misest&auml; ei l&auml;hde automaattista viesti&auml; loppuk&auml;ytt&auml;j&auml;lle. Operaattorin tulee itse ilmoittaa k&auml;ytt&auml;j&auml;lle, kun k&auml;ytt&ouml;oikeus on luotu. T&auml;m&auml;n j&auml;lkeen k&auml;ytt&auml;j&auml; p&auml;&auml;see kirjautumaan Liikenneviraston tunnuksilla j&auml;rjestelm&auml;&auml;n.

![K&auml;ytt&auml;j&auml;n lis&auml;&auml;minen](k20.JPG)

_Uuden k&auml;ytt&auml;j&auml;n lis&auml;&auml;minen._

2. Importit
-----------

Importeilla vied&auml;&auml;n aineistoja j&auml;rjestelm&auml;&auml;n.

2.1 CSV-vienti
--------------

Joukkoliikenteen pys&auml;kkien suomenkielisi&auml; nimi&auml; ja tyyppi&auml; voi p&auml;ivitt&auml;&auml; viem&auml;ll&auml; .csv-tiedoston [k&auml;ytt&ouml;liittym&auml;n](https://testiextranet.liikennevirasto.fi/digiroad/excel_import.html ) kautta j&auml;rjestelm&auml;&auml;n. 

![CSV-vienti](k23.JPG)

_K&auml;ytt&ouml;liittym&auml; .csv-tiedostojen viennille._

1. Klikkaa "selaa"
1. Etsi .csv-tiedosto hakemistostasi.
1. Klikkaa "Lataa tiedot"

Viennin onnistuessa j&auml;rjestelm&auml; ilmoittaa:"CSV tiedosto k&auml;sitelty". Mik&auml;li vienti ep&auml;onnistuu, j&auml;rjestelm&auml; tulostaa virhelokin virheellisist&auml; tiedoista.

Huomioita csv-tiedostosta:

- .csv-tiedoston encoding-valinnan tulee olla "Encode in UTF-8 without BOM"
- Tiedosto on muotoa:

```
Valtakunnallinen ID;Pysäkin nimi;Pysäkin tyyppi
300790;nimi;1,2,2
165833;toinen nimi;5
```
- Tiedot on eroteltu puolipisteell&auml; (;).
- Nimi luetaan merkkijonona.
- Pys&auml;kin tyypit ovat: (1) Raitiovaunu, (2) Linja-autojen paikallisliikenne, (3) Linja-autojen kaukoliikenne, (4) Linja-autojen pikavuoro ja (5) Virtuaalipys&auml;kki.
- Pys&auml;kin tyypit on eroteltu pilkulla.
- Jos tietokent&auml;n j&auml;tt&auml;&auml; tyhj&auml;ksi, j&auml;&auml; pys&auml;kin vanha tieto voimaan.
- Toistaiseksi CSV-vienti&auml; ei kannata tehd&auml; IE-selaimella, koska selain ei tulosta virhelokia.



3. Exportit
-----------

Exporteilla vied&auml;&auml;n aineistoja j&auml;rjestelm&auml;st&auml; ulos.

3.1 Pys&auml;kkitietojen vienti Vallu-j&auml;rjestelm&auml;&auml;n
---------------------------------------------

J&auml;rjestelm&auml; tukee pys&auml;kkitietojen vienti&auml; Vallu-j&auml;rjestelm&auml;&auml;n. Pys&auml;kkitiedot toimitetaan .csv-tiedostona FTP-palvelimelle. Vienti k&auml;ynnistet&auml;&auml;n automaattisesti Jenkins-palvelimella joka päivä klo 19:00 ajamalla 'vallu_import.sh' skripti. Skripti hakee pys&auml;kkitiedot tietokannasta k&auml;ytt&auml;en projektille m&auml;&auml;ritelty&auml; kohdetieto-tietol&auml;hdett&auml;.

FTP-yhteys ja kohdekansio tulee m&auml;&auml;ritell&auml; 'ftp.conf'-tiedostossa joka on tallennettu samaan 'vallu_import.sh' skriptin kanssa. 'ftp.conf'-tiedostossa yhteys ja kohdekansio m&auml;&auml;ritell&auml;&auml;n seuraavalla tavalla:
```
<k&auml;ytt&auml;j&auml;nimi> <salasana> <palvelin ja kohdehakemisto>
```

Esimerkiksi:
```
username password localhost/valluexport
```

Vienti luo FTP-palvelimelle pys&auml;kkitiedot zip-pakattuna .csv-tiedostona nimell&auml; 'digiroad_stops.zip' sek&auml; 'flag.txt'-tiedoston, joka sis&auml;lt&auml;&auml; Vallu-viennin aikaleiman muodossa vuosi (4 merkki&auml;), kuukausi (2 merkki&auml;), p&auml;iv&auml; (2 merkki&auml;), tunti (2 merkki&auml;), minuutti (2 merkki&auml;), sekunti (2 merkki&auml;). Esimerkiksi '20140417133227'.

K&auml;ytt&ouml;&ouml;notto kopioi ymp&auml;rist&ouml;kohtaisen 'ftp.conf'-tiedoston k&auml;ytt&ouml;&ouml;nottoymp&auml;rist&ouml;n deployment-hakemistosta release-hakemistoon osana k&auml;ytt&ouml;&ouml;nottoa. N&auml;in ymp&auml;rist&ouml;kohtaista 'ftp.conf'-tiedostoa, joka sis&auml;lt&auml;&auml; kirjautumistietoja, voidaan yll&auml;pit&auml;&auml; tietoturvallisesti k&auml;ytt&ouml;&ouml;nottopalvelimella. 

### 2.1 XML- viestin l&auml;hetys VALLUun###

Tallentamisen yhteydess&auml; l&auml;hetet&auml;&auml;n VALLU- j&auml;rjestelm&auml;&auml;n xml- viesti.

Vallu l&auml;hetyksen konfiguraatio on ./conf/[ympärist&ouml;]/digiroad2.properties tiedostossa.
```
digiroad2.vallu.server.sending_enabled=true
digiroad2.vallu.server.address=http://localhost:9002
```
L&auml;hetettyjen tietojen logitiedot l&ouml;tyv&auml;t palvelimelta ./logs/vallu-messages.log tiedostosta.

3.2 Pys&auml;kkitietojen vienti LMJ-j&auml;rjestelm&auml;&auml;n
---------------------------------------------------------------

Pys&auml;keist&auml; voi irroittaa kuntarajauksella .txt-tiedostoja LMJ-j&auml;rjestelm&auml;&auml; varten. Irroitusta varten t&auml;ytyy olla kehitysymp&auml;rist&ouml; ladattuna koneelle.

Tarvittavat tiedostot ovat bonecp.properties ja LMJ-import.sh -skripti. Bonecp.properties ei ole avointa l&auml;hdekoodia eli sit&auml; ei voi julkaista GitHubissa eik&auml; siten t&auml;ss&auml; k&auml;ytt&ouml;ohjeessa. Tarvittaessa tiedostoa voi kysy&auml; Taru Vainikaiselta tai kehitystiimilt&auml;. Bonecp.properties tallennetaan sijaintiin:

```
digi-road-2\digiroad2-oracle\conf\properties\
```

Kun bonecp.properties on tallennettu, voidaan LMJ-import.sh-skripti ajaa Linux-ymp&auml;rist&ouml;ss&auml; komentorivill&auml;. Jos k&auml;yt&ouml;ss&auml; on Windows-ymp&auml;rist&ouml;, skriptin&auml; ajetaan:

```
sbt -Ddigiroad2.env=production "runMain fi.liikennevirasto.digiroad2.util.LMJImport <kuntanumerot välillä erotettuna>"
```

Esimerkiksi:
```
 sbt -Ddigiroad2.env=production "runMain fi.liikennevirasto.digiroad2.util.LMJImport 89 90 91"
```
 
Sovellus luo Stops.txt-tiedoston samaan hakemistoon LMJ_import.sh-skriptin kanssa.

4. Kehitysymp&auml;rist&ouml;n asennus
----------------------------

__Projekti GitHubissa__

Projektin kloonaaminen omalle koneelle edellytt&auml;&auml; tunnuksia [GitHubiin](https://github.com/), jossa versionhallinta toteutetaan. Lis&auml;ksi tarvitaan [Git client](http://git-scm.com/downloads) omalle koneelle. Client on k&auml;ytt&ouml;liittym&auml;, joita on olemassa sek&auml; graafisia ett&auml; komentorivipohjaisia. 

Projektin kloonaaminen suoritetaan clientilla. Ensimm&auml;ist&auml; kertaa clienttia k&auml;ytett&auml;ess&auml; suoritetaan seuraavat komennot (komentorivipohjaisissa clienteissa):

M&auml;&auml;ritell&auml;&auml;n nimimerkki, joka n&auml;kyy, kun commitoi uutta koodia GitHubiin. K&auml;yt&auml;nn&ouml;ss&auml; operaattorin ei tarvitse commitoida.

```
git config --global user.name "Nimimerkkisi"
```

Kloonataan projekti omalle koneelle.

```
git clone https://github.com/finnishtransportagency/digi-road-2.git
```

__Kehitysymp&auml;rist&ouml;__

- Asenna [node.js](http://howtonode.org/how-to-install-nodejs) (samalla asentuu npm).

- Asenna bower. Ajetaan komentorivill&auml; komento:

```
npm install -g bower
```

- Hae ja asenna projektin tarvitsemat riippuvuudet:

```
npm install && bower install
```

- Asenna grunt:

```
npm install -g grunt-cli
```

- Alusta ja konfiguroi tietokantaymp&auml;rist&ouml;. Luo tiedosto bonecp.properties sijaintiin: digiroad2/digiroad2-oracle/conf/dev/bonecp.properties. Bonecp.properties sis&auml;lt&auml;&auml; tietokantayhteyden tiedot:

```
bonecp.jdbcUrl=jdbc:oracle:thin:@<tietokannan_osoite>:<portti>/<skeeman_nimi>
bonecp.username=<k&auml;ytt&auml;j&auml;tunnus>
bonecp.password=<salasana>
```

Tietokantayhteyden tiedoista voi kysy&auml; Taru Vainikaiselta.

Tietokanta ja skeema t&auml;ytyy alustaa aika ajoin (huomaa, kun kehitysymp&auml;rist&ouml; ei en&auml;&auml; toimi). Alustus suoritetaan ajamalla fixture-reset.sh-skripti komentorivill&auml;:

```
fixture-reset.sh
```

__Kehitysymp&auml;rist&ouml;n ajaminen__

Kehitysymp&auml;rist&ouml;&auml; ajetaan koneella ajamalla seuraavat komennot aina, kun kehitysymp&auml;rist&ouml; k&auml;ynnistet&auml;&auml;n uudelleen:

Kehitysserverin pystytys:

```
grunt server
```

API-palvelin:

__Windows:__

```
sbt
```

```
container:start
```

__Linux:__

```
./sbt '~;container:start /'
```

Linkit:
------

[Loppuk&auml;ytt&auml;j&auml;n ohje](https://testiextranet.liikennevirasto.fi/digiroad/manual)
 
[L&auml;hdekoodi](https://github.com/finnishtransportagency/digiroad2)


Yhteystiedot
------------

__Digiroadin kehitystiimi:__

digiroad2@reaktor.fi

__Palaute operaattorin manuaalista:__

taru.vainikainen@karttakeskus.fi
