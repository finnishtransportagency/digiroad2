Operaattorin manuaali Digiroad 2 -sovellukseen
----------------------------------------------

1. Uuden k&auml;ytt&auml;j&auml;n lis&auml;&auml;minen
-----------------------------

Vain operaattori-k&auml;ytt&auml;j&auml; voi lis&auml;t&auml; uuden k&auml;ytt&auml;j&auml;n. Uusi k&auml;ytt&auml;j&auml; lis&auml;t&auml;&auml;n osoitteessa:

https://testiextranet.liikennevirasto.fi/digiroad/newuser.html 

K&auml;ytt&ouml;liittym&auml;ss&auml; on lomake, johon tulee t&auml;ydent&auml;&auml; seuraavat tiedot:

1. K&auml;ytt&auml;j&auml;tunnus: K&auml;ytt&auml;j&auml;n tunnus Liikenneviraston j&auml;rjestelmiin
1. Ely nro: ELY:n numero tai pilkulla erotettuna useamman ELY:n numerot (esimerkiksi 1, 2, 3)
1. Kunta nro: Kunnan numero tai pilkulla erotettuna useamman kunnan numerot (esimerkiksi 091, 092)
1. Oikeuden tyyppi: Muokkausoikeus tai Katseluoikeus

Kun lomake on t&auml;ytetty, painetaan "Luo k&auml;ytt&auml;j&auml;". Sovellus ilmoittaa onnistuneesta k&auml;ytt&auml;j&auml;n lis&auml;&auml;misest&auml;. Jos k&auml;ytt&auml;j&auml;ksi lis&auml;t&auml;&auml;n jo olemassa olevan k&auml;ytt&auml;j&auml;n, sovellus poistaa vanhan ja korvaa sen uudella k&auml;ytt&auml;j&auml;ll&auml;.

![K&auml;ytt&auml;j&auml;n lis&auml;&auml;minen](k20.JPG)

_Uuden k&auml;ytt&auml;j&auml;n lis&auml;&auml;minen._

2. Pys&auml;kkitietojen vienti Vallu-j&auml;rjestelm&auml;&auml;n
---------------------------------------------

J&auml;rjestelm&auml; tukee pys&auml;kkitietojen vienti&auml; Vallu-j&auml;rjestelm&auml;&auml;n. Pys&auml;kkitiedot toimitetaan .csv-tiedostona FTP-palvelimelle. Vienti k&auml;ynnistet&auml;&auml;n ajamalla 'vallu_import.sh' skripti. Skripti hakee pys&auml;kkitiedot tietokannasta k&auml;ytt&auml;en projektille m&auml;&auml;ritelty&auml; kohdetieto-tietol&auml;hdett&auml;.

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

3. Pys&auml;kkitietojen vienti LMJ-j&auml;rjestelm&auml;&auml;n
---------------------------------------------------------------

Pys&auml;keist&auml; voi irroittaa kuntarajauksella .txt-tiedostoja LMJ-j&auml;rjestelm&auml;&auml; varten. Irroitusta varten t&auml;ytyy olla kehitysymp&auml;rist&ouml; ladattuna koneelle.

Tarvittavat tiedostot ovat bonecp.properties ja vallu_import.sh -skripti. Bonecp.properties ei ole avointa l&auml;hdekoodia eli sit&auml; ei voi julkaista GitHubissa eik&auml; siten t&auml;ss&auml; k&auml;ytt&ouml;ohjeessa. Tarvittaessa tiedostoa voi kysy&auml; Taru Vainikaiselta tai kehitystiimilt&auml;. Bonecp.properties tallennetaan sijaintiin:

```
digi-road-2\digiroad2-oracle\conf\properties\
```

Kun bonecp.properties on tallennettu, voidaan vallu_import.sh-skripti ajaa Linux-ymp&auml;rist&ouml;ss&auml; komentorivill&auml;. Jos k&auml;yt&ouml;ss&auml; on Windows-ymp&auml;rist&ouml;, skriptin&auml; ajetaan:

```
sbt -Ddigiroad2.env=production "runMain fi.liikennevirasto.digiroad2.util.LMJImport <kuntanumerot välillä erotettuna>"
```

Esimerkiksi:
```
 sbt -Ddigiroad2.env=production "runMain fi.liikennevirasto.digiroad2.util.LMJImport 89 90 91"
```
 
Sovellus luo Stops.txt-tiedoston samaan hakemistoon vallu_import.sh-skriptin kanssa.

Linkki k&auml;ytt&ouml;ohjeeseen
--------------------------------

https://devtest.liikennevirasto.fi/digiroad/manual
 
L&auml;hdekoodi
----------

Digiroad 2 -sovelluksen avoin l&auml;hdekoodi l&ouml;ytyy GitHubista:

https://github.com/finnishtransportagency/digiroad2

Yhteystiedot
------------

__Digiroadin kehitystiimi:__

digiroad2@reaktor.fi

__Palaute operaattorin manuaalista:__

taru.vainikainen@karttakeskus.fi
