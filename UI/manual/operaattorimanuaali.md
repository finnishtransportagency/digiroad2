Operaattorin k&auml;ytt&ouml;ohje
=========================

Digiroad2-j&auml;rjestelm&auml; koostuu ominaisuustietojen hallinnasta (OTH) ja V&auml;yl&auml;verkon hallinnasta (VVH). T&auml;ss&auml; ohjeessa kerrotaan OTH:n operaattorille tarkoitettuja, tarkempia ohjeita.

OTH hy&ouml;dynt&auml;&auml; VVH:ta seuraavasti:
VVH:n testikanta --> OTH:n testikanta
VVH:n tuotantokanta --> OTH:n koulutuskanta ja tuotantokanta

OTH:n eri ymp&auml;rist&ouml;jen osoitteet selaimessa:
Testikanta https://devtest.liikennevirasto.fi/digiroad/
Koulutuskanta https://apptest.liikennevirasto.fi/digiroad/
Tuotantokanta https://testiextranet.liikennevirasto.fi/digiroad/

Uudet versiot menev&auml;t ensin testikantaan, jossa testaaja tarkistaa version toimivuuden. T&auml;m&auml;n j&auml;lkeen uusi k&auml;ytt&ouml;ohje p&auml;ivitet&auml;&auml;n testikantaan. Toimiva versio vied&auml;&auml;n koulutuskantaan ja tuotantokantaan, eli niiden versiot ovat aina identtiset.

Kaikki Digiroad-yll&auml;pitosovelluksen k&auml;ytt&ouml;liittym&auml;&auml;n liittyv&auml;t sivut toimivat kaikissa ymp&auml;rist&ouml;iss&auml; (sovellus, k&auml;ytt&ouml;ohje, uuden k&auml;ytt&auml;j&auml;n lis&auml;&auml;minen jne.). Vaihtamalla osoitteen alkuun devtest, apptest tai testiextranet voi valita, mihin ymp&auml;rist&ouml;&ouml;n menee. T&auml;ss&auml; ohjeessa olevat linkit ovat tuotantoymp&auml;rist&ouml;&ouml;n (testiextranet).

__Huom! Kaikki ymp&auml;rist&ouml;t n&auml;ytt&auml;v&auml;t selaimessa p&auml;&auml;lisin puolin samalta, joten tulee olla tarkkana, mihin ymp&auml;rist&ouml;&ouml;n muutoksia tekee!__

Ohjeessa on useassa kohdassa mainittu, ett&auml; tunnuksien hallinta on Digiroad2-kehitystiimill&auml; ja ne saa osoitteesta digiroad2@reaktor.fi. T&auml;m&auml; tilanne tulee muuttumaan, kun kehitysprojekti p&auml;&auml;ttyy ja siirryt&auml;&auml;n yll&auml;pitovaiheeseen, mutta toistaiseksi kehitykseen liittyvien tilien/sivujen tunnusten ja salasanojen hallinta on kehitystiimiss&auml;.

1. Uuden k&auml;ytt&auml;j&auml;n lis&auml;&auml;minen
-----------------------------

Vain operaattori-k&auml;ytt&auml;j&auml; voi lis&auml;t&auml; uuden k&auml;ytt&auml;j&auml;n. Uusi k&auml;ytt&auml;j&auml; lis&auml;t&auml;&auml;n [k&auml;ytt&auml;j&auml;nhallinnassa.](https://testiextranet.liikennevirasto.fi/digiroad/newuser.html ) K&auml;ytt&auml;j&auml;nhallinnassa lis&auml;tyt k&auml;ytt&auml;j&auml;t ovat Premium-k&auml;ytt&auml;ji&auml;, joilla on oikeudet muokata m&auml;&auml;ritellyill&auml; alueilla kaikkia aineistoja.

K&auml;ytt&ouml;liittym&auml;ss&auml; on lomake, johon tulee t&auml;ydent&auml;&auml; seuraavat tiedot:

1. K&auml;ytt&auml;j&auml;tunnus: K&auml;ytt&auml;j&auml;n tunnus Liikenneviraston j&auml;rjestelmiin
1. Ely nro: ELY:n numero tai pilkulla erotettuna useamman ELY:n numerot (esimerkiksi 1, 2, 3), Ely-taulukko alla
1. Kunta nro: Kunnan numero tai pilkulla erotettuna useamman kunnan numerot (esimerkiksi 091, 092). TVV-alueiden kuntanumerot alla.
1. Oikeuden tyyppi: Muokkausoikeus (tai Katseluoikeus)*

*Katseluoikeuksia ei lis&auml;t&auml; toistaiseksi. Kaikilla k&auml;ytt&auml;jill&auml;, joilla on Livin extranet-tunnus, on oikeudet katsella Digiroadia (ns. visitor-rooli, k&auml;ytt&auml;j&auml;&auml; ei ole tallennettu Digiroad-yll&auml;pitosovelluksen tietokantaan). Katselemaan p&auml;&auml;see kirjautumalla sovellukseen extranet-tunnuksilla.

Kun lomake on t&auml;ytetty, painetaan "Luo k&auml;ytt&auml;j&auml;". Sovellus ilmoittaa onnistuneesta k&auml;ytt&auml;j&auml;n lis&auml;&auml;misest&auml;. Jos k&auml;ytt&auml;j&auml;ksi lis&auml;t&auml;&auml;n jo olemassa olevan k&auml;ytt&auml;j&auml;, sovellus poistaa vanhan ja korvaa sen uudella k&auml;ytt&auml;j&auml;ll&auml;. K&auml;ytt&auml;j&auml;n lis&auml;&auml;misest&auml; ei l&auml;hde automaattista viesti&auml; loppuk&auml;ytt&auml;j&auml;lle. Operaattorin tulee itse ilmoittaa k&auml;ytt&auml;j&auml;lle, kun k&auml;ytt&ouml;oikeus on luotu. T&auml;m&auml;n j&auml;lkeen k&auml;ytt&auml;j&auml; p&auml;&auml;see kirjautumaan Liikenneviraston tunnuksilla j&auml;rjestelm&auml;&auml;n.

Jos halutaan lis&auml;t&auml; k&auml;ytt&auml;j&auml;lle oikeudet vain pys&auml;kkiaineistoon, lis&auml;t&auml;&auml;n k&auml;ytt&auml;j&auml;lle oikeudet ensin k&auml;ytt&ouml;liittym&auml;ss&auml;, ja poistetaan sen j&auml;lkeen tietokannassa k&auml;ytt&auml;j&auml;lt&auml; "premium"-parametri.

Jos halutaan lis&auml;t&auml; operaattori-k&auml;ytt&auml;j&auml;, lis&auml;t&auml;&auml;n k&auml;ytt&auml;j&auml; ensin k&auml;ytt&ouml;liittym&auml;ss&auml;, ja sen j&auml;lkeen lis&auml;t&auml;&auml;n k&auml;ytt&auml;j&auml;lle parametrin "premium" tilalle parametri "operator" tietokannassa. Operaattori-k&auml;ytt&auml;j&auml;lle ei tarvitse antaa kuntalistausta, koska operator-parametri takaa p&auml;&auml;syn kaikkiin k&auml;ytt&ouml;liittym&auml;n ominaisuuksiin.

Huom! Digiroad2-sovelluksen k&auml;ytt&auml;j&auml;nhallintasivu ja k&auml;ytt&auml;j&auml;nhallinta tietokantatasolla eiv&auml;t ole yhteydess&auml; Liikenneviraston k&auml;ytt&auml;j&auml;nhallintaan. On hyv&auml; tiedostaa, ett&auml; n&auml;m&auml; ovat kaksi eri asiaa. Liikenneviraston k&auml;ytt&auml;j&auml;nhallinnan avulla k&auml;ytt&auml;j&auml; p&auml;&auml;see kirjautumaan Digiroad-yll&auml;pitosovellukseen. Digiroadin omassa k&auml;ytt&auml;j&auml;nhallinassa m&auml;&auml;ritell&auml;&auml;n, mihin kaikkialle k&auml;ytt&auml;j&auml;ll&auml; on oikeus tehd&auml; muutoksia Digiroadissa. K&auml;ytt&auml;j&auml;nhallintasivu (newuser.html) ei siis tarkista, onko ko. k&auml;ytt&auml;j&auml;tunnusta oikeasti olemassa Liikenneviraston k&auml;ytt&auml;j&auml;nhallinnassa. Samoin, jos k&auml;ytt&auml;j&auml;n extranet-tunnus poistuu voimasta Liikenneviraston k&auml;ytt&auml;j&auml;nhallinnassa, tulee se poistaa erikseen Digiroad2:sen tietokannasta. Poistuneella k&auml;ytt&auml;j&auml;tunnuksella ei kuitenkaan p&auml;&auml;se kirjautumaan Digiroadiin, koska Liikenneviraston k&auml;ytt&auml;j&auml;nhallinnasta ei p&auml;&auml;se l&auml;pi sill&auml;.

![K&auml;ytt&auml;j&auml;n lis&auml;&auml;minen](k20.JPG)

_Uuden k&auml;ytt&auml;j&auml;n lis&auml;&auml;minen._

|Ely|Elyn numero|
|---|-----------|
|Ahvenanmaa|0|
|Lapin ELY-keskus|1|
|Pohjois-Pohjanmaan ELY-keskus|2|
|Etel&auml;-Pohjanmaan ELY-keskus|3|
|Keski-Suomen ELY-keskus|4|
|Pohjois-Savon ELY-keskus|5|
|Pirkanmaan ELY-keskus|6|
|Varsinais-Suomen ELY-keskus|7|
|Kaakkois-Suomen ELY-keskus|8|
|Uudenmaan ELY-keskus|9|


|TVV|Kuntanumerot|
|---|------------|
|Oulun TVV|436,244,425,494,859,139,564|
|Jyv&auml;skyl&auml;n TVV|179,500,410|
|Turun TVV|202,853,529,423,704,680|
|Porin TVV|271,609,531,79,886|
|Lahden TVV|316,398,81,98,16,576,781,532,283,111,560|
|Tampereen TVV|211,980,837,562,604,922,418,536|
|Kuopion TVV|297,749|
|Joensuun TVV|167,276,426|


2. CSV-import pys&auml;kkien ominaisuustiedoille
-----------

Importeilla tuodaan aineistoja j&auml;rjestelm&auml;&auml;n.

Joukkoliikenteen pys&auml;kkien suomenkielist&auml; nime&auml;, ruotsinkielist&auml; nime&auml;, liikenn&ouml;intisuuntaa, yll&auml;pit&auml;j&auml;n tunnusta, LiVi-tunnusta, matkustajatunnusta, tyyppi&auml; ja varusteita voi p&auml;ivitt&auml;&auml; tuomalla .csv-tiedoston [k&auml;ytt&ouml;liittym&auml;n](https://testiextranet.liikennevirasto.fi/digiroad/excel_import.html ) kautta j&auml;rjestelm&auml;&auml;n. Oletusarvoisesti j&auml;rjestelm&auml; p&auml;ivitt&auml;&auml; kaikilla v&auml;yl&auml;tyypeill&auml; olevia pys&auml;kkej&auml;. P&auml;ivitett&auml;vi&auml; pys&auml;kkej&auml; voi rajata my&ouml;s sen mukaan, mill&auml; v&auml;yl&auml;tyypill&auml; ne sijaitsevat. Rajoitus tehd&auml;&auml;n valitsemalla k&auml;ytt&ouml;liittym&auml;st&auml; halutut v&auml;yl&auml;tyypit.

![CSV-tuonti](k23.JPG)

_K&auml;ytt&ouml;liittym&auml; .csv-tiedostojen tuonnille._

1. Klikkaa "selaa"
1. Etsi .csv-tiedosto hakemistostasi.
1. Klikkaa "Lataa tiedot"

Tietoja k&auml;sitelless&auml;&auml;n sovellus ilmoittaa:"Pys&auml;kkien lataus on k&auml;ynniss&auml;. P&auml;ivit&auml; sivu hetken kuluttua uudestaan". Kun sivun p&auml;ivitys onnistuu, sovellus on k&auml;sitellyt koko tiedoston.

Tuonnin onnistuessa j&auml;rjestelm&auml; ilmoittaa:"CSV tiedosto k&auml;sitelty". Mik&auml;li tuonti ep&auml;onnistuu, j&auml;rjestelm&auml; tulostaa virhelokin virheellisist&auml; tiedoista. CSV-tuontia ei kannata tehd&auml; IE-selaimella, koska selain ei tulosta virhelokia.

Huomioita csv-tiedostosta:

- CSV-tuonti ei toimi oikein, jos tiedostossa on pys&auml;kkej&auml;, jotka ovat irti geometriasta (floating)
- .csv-tiedoston encoding-valinnan tulee olla "Encode in UTF-8 without BOM", encodingin saa vaihettua esimerkiksi Notepad++:lla
- Tiedoston tulee sis&auml;lt&auml;&auml; kaikki tietokent&auml;t, vaikka niit&auml; ei p&auml;ivitett&auml;isik&auml;&auml;n. Esimerkki:

```
Valtakunnallinen ID;Pys&auml;kin nimi;Pys&auml;kin nimi ruotsiksi;Tietojen yll&auml;pit&auml;j&auml;;Liikenn&ouml;intisuunta;Yll&auml;pit&auml;j&auml;n tunnus;LiVi-tunnus;Matkustajatunnus;Pys&auml;kin tyyppi;Aikataulu;Katos;Mainoskatos;Penkki;Py&ouml;r&auml;teline;S&auml;hk&ouml;inen aikataulun&auml;ytt&ouml;;Valaistus;Saattomahdollisuus henkil&ouml;autolla;Lis&auml;tiedot
165280;pys&auml;kin nimi;stops namn;1;etel&auml;&auml;n;HSL321;LIVI098;09876;2,4;1;2;1;99;2;1;2;1; Lis&auml;tietokentt&auml;&auml;n saa sy&ouml;tt&auml;&auml; vapaata teksti&auml;, joka saa sis&auml;lt&auml;&auml; merkkej&auml;(;:!(&), numeroita(1234) ja kirjaimia(AMSKD).
```
- Tiedot on eroteltu puolipisteell&auml; (;).
- Nimi suomeksi ja ruotsiksi, liikenn&ouml;intisuunta, yll&auml;pit&auml;j&auml;n tunnus, LiVi-tunnus ja matkustajatunnus luetaan merkkijonona.
- Tietojen yll&auml;pit&auml;j&auml; -kent&auml;n arvot ovat: (1) Kunta, (2) ELY-keskus, (3) Helsingin seudun liikenne, (99) Ei tiedossa.
- Pys&auml;kin tyypin arvot ovat: (1) Raitiovaunu, (2) Linja-autojen paikallisliikenne, (3) Linja-autojen kaukoliikenne, (4) Linja-autojen pikavuoro ja (5) Virtuaalipys&auml;kki.
- Pys&auml;kin tyypit on eroteltu pilkulla.
- Varusteet (aikataulu, katos, mainoskatos, penkki, py&ouml;r&auml;teline, s&auml;hk&ouml;inen aikataulun&auml;ytt&ouml;, valaistus ja saattomahdollisuus henkil&ouml;autolla) ilmoitetaan koodiarvoina: (1) Ei, (2) Kyll&auml; tai (99) Ei tietoa.
- Lis&auml;tiedot-kentt&auml;&auml;n voi tallentaa vapaata teksti&auml;, joka saa sis&auml;lt&auml;&auml; maksimissaan 4000 merkki&auml;. Huomioitavaa on, ett&auml; &auml;&auml;kk&ouml;set viev&auml;t kaksi merkki&auml;. Jos teksti sis&auml;lt&auml;&auml; puolipisteit&auml; (;) t&auml;ytyy teksti kirjoittaa lainausmerkkien("") sis&auml;&auml;n, jotta koko teksti tallentuu tietokantaan.
- Jos tietokent&auml;n j&auml;tt&auml;&auml; tyhj&auml;ksi, j&auml;&auml; pys&auml;kin vanha tieto voimaan.


3. Pys&auml;kkien exportit
-----------

Exporteilla vied&auml;&auml;n aineistoja j&auml;rjestelm&auml;st&auml; ulos.

##3.1 Pys&auml;kkitietojen CSV (ns. Vallu-CSV)##

Vallu-j&auml;rjestelm&auml; ei en&auml;&auml; hy&ouml;dynn&auml; t&auml;t&auml; CSV-tiedostoa, mutta CSV-tiedosto luodaan joka y&ouml; operaattorin omaan k&auml;ytt&ouml;&ouml;n ja jaettavaksi Digiroadin nettisivuille. Pys&auml;kki-CSV export k&auml;ynnistyy klo 00:00 joka y&ouml; Livin FME Serverill&auml;. CSV:n tekeminen kest&auml;&auml; noin 5 tuntia. Lopputulos vied&auml;&auml;n Digiroad2:sen ftp-palvelimelle. Jos export ep&auml;onnistuu, FME-ajo katkeaa eik&auml; tiedostoa vied&auml; ftp:lle.

###Pys&auml;kki-CSV:n tietolajit###

Pys&auml;kki-CSV:n tietolajit voi lukea Digiroadin nettisivuilla olevasta taulukosta: http://www.digiroad.fi/Uusi_DR/pysakki/fi_FI/pysakki/


##3.2 Pys&auml;kkimuutosten l&auml;hetys Vallu-j&auml;rjestelm&auml;&auml;n##

Pys&auml;kin tietoja muokattaessa muutoksista l&auml;htee v&auml;litt&ouml;m&auml;sti Vallu-j&auml;rjestelm&auml;&auml;n XML-sanoma, jossa ovat muutettujen pys&auml;kkien tiedot.

Muuttuneita tietoja voi tarkastella lokista: https://testiextranet.liikennevirasto.fi/digiroad/vallu-server.log (tuotanto) ja https://devtest.liikennevirasto.fi/digiroad/vallu-server.log (testi). Vallu-XML-lokia ei ole koulutuskannassa.
Vallu-XML-logiin eiv&auml;t mene raitiovaunupys&auml;keille tehdyt muutokset. Lis&auml;ksi Digiroadin ja Vallun Pys&auml;kkieditorin v&auml;lill&auml; on s&auml;&auml;nt&ouml;j&auml;, jotka est&auml;v&auml;t Vallu-XML:st&auml; tulevan muutoksen siirtymist&auml; Valluun. N&auml;ist&auml; voi tarvittaessa kysy&auml; Hannelelta tai Liikenneviraston Teemu Peltoselta.

Vallu l&auml;hetyksen konfiguraatio on ./conf/[ymp&auml;rist&ouml;]/digiroad2.properties tiedostossa.
```
digiroad2.vallu.server.sending_enabled=true
digiroad2.vallu.server.address=http://localhost:9002
```
L&auml;hetettyjen tietojen logitiedot l&ouml;tyv&auml;t palvelimelta ./logs/vallu-messages.log tiedostosta. 

##3.3 Pys&auml;kkitiedot GTFS-muodossa Waltti-j&auml;rjestelm&auml;&auml;n##

Waltti-j&auml;rjestelm&auml;&auml;n pys&auml;kit luetaan GTFS-muodossa. GTFS-formaatista voi lukea lis&auml;&auml; seuraavista linkeist&auml;:
https://en.wikipedia.org/wiki/General_Transit_Feed_Specification
https://developers.google.com/transit/gtfs/
https://developers.google.com/transit/gtfs/reference ja t&auml;&auml;lt&auml; https://developers.google.com/transit/gtfs/reference#stops_fields Digiroadista toimitetaan Walttiin stops.txt-tiedosto

Waltti-j&auml;rjestelm&auml;&auml; varten hy&ouml;dynt&auml;j&auml;t voivat hakea aineiston Liikenneviraston aineistojen latauspalvelusta http://aineistot.liikennevirasto.fi/gtfs/. Aineisto tehd&auml;&auml;n Livin FME Serverill&auml; ajastetusti, ja se p&auml;ivittyy sivustolle joka aamu TVV-aluekohtaisina paketteina.

Tarvittaessa Waltti-irrotuksen voi tehd&auml; my&ouml;s omalla koneella valmiista FME-ty&ouml;tilasta.


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

5. Geckoboard
-----------

Digiroadin tuotantoymp&auml;rist&ouml;n tilaa voi seurata Geckoboardilta https://reaktor.geckoboard.com/dashboards/C33E233E34A644EE (ei vaadi kirjautumista). Geckoboardista voi esimerkiksi etsi&auml; apua sovelluksen hitausongelmien selvityksess&auml;. Geckoboardin sis&auml;lt&ouml; on selitetty alempana kuvassa olevien numeroiden perusteella. Tiedot ker&auml;t&auml;&auml;n New Relicist&auml;, Google Analyticsist&auml; ja Jenkinsist&auml;. New Relic monitoroi Digiroadin testikannan ja tuotantokannan tilaa. Esimerkiksi suorituskykyongelmia voi tutkia tarkemmin New Relicin avulla. New Reliciin on tunnukset operaattoripalvelussa Mika Lehtosella. Google Analyticsist&auml; kerrotaan tarkemmin kappaleessa 7. Jenkinsiin ei ole toistaiseksi p&auml;&auml;sy&auml; operaattorilla, mutta tulee my&ouml;hemmin.

![Geckoboard.](k166.jpg)

_Geckoboardin osiot._

1. Appdex Score kertoo sovelluksen suorituskyvyst&auml;. Mit&auml; l&auml;hemp&auml;n&auml; luku on 1:st&auml;, sit&auml; parempi. Noin 0.7:n kohdalla sovellus alkaa hidastella niin, ett&auml; ty&ouml;skentely vaikeutuu huomattavasti. L&auml;hde: New Relic
2. Current Visitor Gauge kertoo t&auml;m&auml;n hetkisten aktiivisten k&auml;ytt&auml;jien lukum&auml;&auml;r&auml;n. Oikean laidan luku on k&auml;ytt&auml;jien lukum&auml;&auml;r&auml;n enn&auml;tys. L&auml;hde: Google Analytics
3. Avg. Time on Site (today), keskim&auml;&auml;r&auml;inen vierailuaika sivulla per k&auml;ytt&auml;j&auml; t&auml;m&auml;n p&auml;iv&auml;n aikana. L&auml;hde: Google Analytics
4. Bounce Rate (Today) ei ole kiinnostava tieto, koska Digiroad-sovellus on p&auml;&auml;asiassa yhdell&auml; sivulla. L&auml;hde: Google Analytics
5. Production VVH Response Times on VVH:n vasteajat viimeisen puolen tunnin ajalta. Vasemman laidan asteikko on siniselle viivalle ja se on millisekunteja. Siniset piikit ja tuhansiin kohoavat millisekunnit kertovat, ett&auml; VVH:n vasteajat ovat korkeita ja sovellus todenn&auml;k&ouml;isesti on hidas. L&auml;hde: New Relic
6. Production VVH Response Times Weekly on VVH:n vasteajat viimeisen viikon ajalta. Tarkastellaan samalla tavalla, kuin ylemp&auml;&auml; asteikkoa. Y&ouml;lliset piikit johtuvat joka&ouml;isten pys&auml;kkiexporttien tekemisest&auml;. L&auml;hde: New Relic
7. Viimeisimmät buildit. Lähde: Jenkins
8. Buildien tilanne. L&auml;hde: Jenkins
9. Unique Visitors (today) on p&auml;iv&auml;n yksil&ouml;lliset k&auml;vij&auml;t. L&auml;hde: Google Analytics
10.Unique Visitors (30D) on viimeisen 30 p&auml;iv&auml;n yksil&ouml;lliset k&auml;vij&auml;t. L&auml;hde: Google Analytics
11. Reaktorin Leuat, jepjep... :)


6. DR2:n Google-tili
--------------------

Digiroad 2:lla on oma Google-tili: Digiroad2@gmail.com. Tili on edellytys, jotta Google Streetview:ll&auml; on mahdollista ladata muutama tuhat kuvaa p&auml;iv&auml;ss&auml;. My&ouml;s Digiroad2:sen Google Driven ja Google Analyticsin omistajuudet ovat ko. tilill&auml;. 

Tunnuksia Google-tiliin voi kysy&auml; kehitystiimilt&auml;: digiroad2@reaktor.fi.

7. Google Analytics
-------------------

Digiroad2-sovellus ja siihen liittyv&auml;t sivustot (mm. floating stops ja k&auml;ytt&ouml;ohje) on kytketty [Google Analyticsiin](https://www.google.com/analytics/) . Google Analyticsin avulla voi seurata sovelluksen k&auml;ytt&ouml;&auml; ja k&auml;ytt&auml;j&auml;m&auml;&auml;ri&auml;. Google Analyticsi&auml; p&auml;&auml;see katsomaan Digiroadin omilla gmail-tunnuksilla digiroad.ch5@gmail.com (salasana operaattorilta) tai digiroad2@gmail.com (salasana kehitystiimilt&auml;).

Google Analyticsiin on kytketty kaikki Digiroad2:sen ymp&auml;rist&ouml;t:

-	Production: tuotantokanta, osoite selaimessa testiextranet -alkuinen
-	Staging: testikanta, osoite selaimessa devtest -alkuinen
-	Training: koulutuskanta, osoite selaimessa apptest –alkuinen

Kunkin ymp&auml;rist&ouml;n tilastoihin p&auml;&auml;see k&auml;siksi painamalla sen alta kohtaa ”All Web Site Data”. Ymp&auml;rist&ouml;n valinnan j&auml;lkeen vasemman laidan valikoista voi tarkastella joko reaaliaikaista tilannetta (viimeiset 30 min) tai aiempaa tilannetta joltain aikav&auml;lilt&auml;. Aikav&auml;li&auml; voi muokata oikeasta yl&auml;kulmasta p&auml;iv&auml;m&auml;&auml;r&auml;n tarkkuudella. T&auml;ss&auml; on esitelty muutamia tapoja hy&ouml;dynt&auml;&auml; Google Analyticsi&auml;, mutta eri valikoista l&ouml;yt&auml;&auml; my&ouml;s paljon muita tietoja.

Reaaliaikaisen tilanteen n&auml;kee Reaaliaikainen-valikosta (1). T&auml;&auml;lt&auml; voi tarkastella sek&auml; k&auml;ytt&auml;jien sijainteja ett&auml; tapahtumia. Tapahtumissa Tapahtumaluokka –sarakkeen alta voi klikata tietolajikohtaisesti, mit&auml; ko. tietolajiin kohdistuvia tapahtumia on juuri nyt katselun tai muokkauksen kohteena sovelluksessa. Tietolajia voi klikata erikseen (esim. speedLimit), jolloin p&auml;&auml;see katsomaan, paljonko kyseiseen tietolajiin kohdistuu tapahtumia. Tietolajikohtaisesti on huomioitava, ett&auml; s-p&auml;&auml;tteinen versio (speedLimits, assets, axleWeightLimits yms.) n&auml;ytt&auml;&auml; ruudulle haettujen kohteiden tapahtumat ja tietolajin alta ilman s-kirjainta (speedLimit, asset, axleWeightLimit yms.) l&ouml;yt&auml;&auml; muokkauksen, tallennuksen, siirtojen, uusien luomisen yms. tapahtumat (2). Valinnat voi tyhjent&auml;&auml; ruksista yl&auml;reunasta (3). Tapahtumia voi tarkastella k&auml;ytt&auml;jien lukum&auml;&auml;r&auml;n mukaan tai tapahtumien lukum&auml;&auml;r&auml;n mukaan (4).

![googleanalytics1](googleanalytics1.jpg)

Yleis&ouml;-valikon (5) Yleiskatsaus-kohdasta voi katsoa esimerkiksi kaupunkikohtaisia k&auml;vij&auml;m&auml;&auml;ri&auml; (7) ja selaintietoja kohdasta J&auml;rjestelm&auml; (8). N&auml;ist&auml; l&ouml;yt&auml;&auml; my&ouml;s tarkempia tietoja vasemman laidan valikosta kohdasta Maantieteelliset ja selaintiedoista kohdasta Teknologia. 

![googleanalytics2](googleanalytics2.jpg)

K&auml;ytt&auml;ytyminen-valikon (6) Yleiskatsaus-kohdasta voi katsoa eri Digiroad2-sovellukseen liittyvien sivujen avauskertoja osoitekohtaisesti tai sivun otsikon mukaan (9). Vasemman laidan valikosta kohdasta Tapahtumat voi katsoa tietyn aikav&auml;lin kymmenen yleisint&auml; tapahtumaa. Rajaamalla aikaikkunaa oikeasta yl&auml;kulmasta n&auml;kee my&ouml;s esimerkiksi tietyn viikon yleisimm&auml;t tapahtumat.

![googleanalytics3](googleanalytics3.jpg)

8. Tietolajikohtaisia tarkempia tietoja
-------------------

Digiroad-sovelluksessa on tietolajikohtaisesti joitakin k&auml;sittelys&auml;&auml;nt&ouml;j&auml; kohteille mm. tilanteissa, kun geometria vaihtuu. T&auml;h&auml;n kappaleeseen on kirjattu n&auml;it&auml; operaattori-k&auml;ytt&auml;jien tietoon.

##8.1 Tielinkit##

Kun geometria p&auml;ivittyy, ne linkit joiden MML-ID on edelleen sama, eiv&auml;t tule millek&auml;&auml;n korjauslistalle eik&auml; niit&auml; saateta siten operaattorin tai yll&auml;pit&auml;jien tietoon. N&auml;ille linkeille j&auml;&auml; siis edelleen sama toiminnallinen luokka, linkkityyppi ja liikennevirran suunta. 

Joillekin uusille tielinkeille generoidaan automaattisesti Maanmittauslaitoksen kohdeluokka-tiedosta toiminnallinen luokka ja linkkityyppi:

-   Kohdeluokka ajopolku t&auml;ydennet&auml;&auml;n Digiroad-sovelluksessa ajopoluksi sek&auml; toiminnalliselta luokalta ett&auml; linkkityypilt&auml;
-   Kohdeluokka ajotielle t&auml;ydennet&auml;&auml;n Digiroad-sovelluksessa toiminnallinen luokka muu yksityistie ja linkkityyppi yksiajoratainen tie
-   Kohdeluokka k&auml;vely- ja py&ouml;r&auml;tie t&auml;ydennet&auml;&auml;n Digiroad-sovelluksessa kevyen liikenteen v&auml;yl&auml;ksi sek&auml; toiminnalliselta luokalta ett&auml; linkkityypilt&auml;

Lis&auml;ksi kaikille uusille tielinkeille otetaan liikennevirran suunta -tieto Maanmittauslaitokselta VVH:n rajapinnasta. Jos Maanmittauslaitokselta tullutta tietoa liikennevirran suunnalle muokataan Digiroad-yll&auml;pitosovelluksessa, ei Maanmittauslaitoksen t&auml;st&auml; eri&auml;v&auml; tieto kumoa Digiroadissa olevaa tietoa (ns. override, tietokantaan tallennetaan MML:n tiedon korvaava tieto). N&auml;in varmistetaan, etteiv&auml;t yll&auml;pit&auml;jien tekem&auml;t muutokset kumoudu Maanmittauslaitoksen virheellisell&auml; tiedolla.

Tielinkeille p&auml;ivitet&auml;&auml;n korjattavien linkkien lista (incomplete_links.html) automaattisesti joka aamu tuotanto-, testi- ja koulutusymp&auml;rist&ouml;&ouml;n klo 7:00. P&auml;ivitys kest&auml;&auml; noin tunnin, eik&auml; se vaikuta ty&ouml;skentelyyn Digiroad-yll&auml;pitosovelluksessa.

##8.2 Pistem&auml;iset tietolajit##

Sovellusten pistem&auml;isill&auml; tietolajeilla on kaikilla sama logiikka, mink&auml; perusteella sovellus katsoo niiden olevan irti geometriasta. Pistem&auml;isi&auml; tietolajeja ovat esimerkiksi joukkoliikenteen pys&auml;kki, suojatie ja rautatien tasoristeys.

Kun pistett&auml; tarkastellaan kartalla, ajetaan skripti tietokannassa tai kysell&auml;&auml;n kohteita Kalpa-Apista, sovellus tarkastaa, ovatko pisteet kiinni geometriassa. Geometriasta irti olevaa pistett&auml; kutsutaan "kelluvaksi". Tarkastus perustuu siihen, ett&auml; pisteelle on tallennettu tietokantaan x,y -koordinaatteina sijaintitieto, johon sijaintia nykyisell&auml; geometrialla voidaan verrata. Tarkastus toimii seuraavalla logiikalla:

1. L&ouml;ytyyk&ouml; ko. pisteen linkin MML-ID:ll&auml; edelleen tielinkki ja kuntakoodi s&auml;ilyy samana.
    a. Ei l&ouml;ydy -> Kelluu
    b. L&ouml;ytyy -> Tarkastus siirtyy seuraavaan kohtaan
1. Pisteen sijainnin tarkistus linkill&auml;: sijainti tietokantaan tallennettujen x,y-koordinaattien ja linkin geometrian + pisteen m-arvon v&auml;lill&auml;
    a. Jos et&auml;isyys tietokantaan tallennettujen koordinaattien ja linkin geometrian + pys&auml;kin m-arvon v&auml;lill&auml; on yli 3 metri&auml; -> Kelluu
    b. Jos et&auml;isyys alle tai 3 metri&auml;, pisteen katsotaan olevan tarpeeksi l&auml;hell&auml; samaa sijaintia kuin ennen geometrian p&auml;ivittymist&auml; ja se ei kellu.

Kelluvien pys&auml;kkien lista (floatingstops.html) p&auml;ivittyy automaattisesti tuotantokannassa joka y&ouml; samassa yhteydess&auml;, kun ylemp&auml;n&auml; esitelty pys&auml;kki-csv (Vallu-export) ajetaan.

Muiden pisteiden kelluvien listat p&auml;ivittyv&auml;t esim. kyselem&auml;ll&auml; kohteet l&auml;pi Kalpa-Apista (esim. FME-ajon avulla).
    
##8.3 Nopeusrajoitus##

Nopeusrajoitukset venytet&auml;&auml;n aina linkin mittaisiksi, jos nopeusrajoitus katkeaa ennen linkin alkua tai loppua ja linkill&auml; on vain yksi nopeusrajoitus. T&auml;t&auml; ominaisuutta ei ole muilla viivamaisilla tietolajeilla.

9. Kalpa-API
--------------

Digiroad-tietokannassa on olemassa Kalpa-API niminen JSON-rajapinta, josta on mahdollista hakea jokaisen tietolajin tiedot kunnittain esimerkiksi FME-ty&ouml;tilaan. Kalpa-APIsta haetaan Digiroad-tietolajien tiedot julkaisua varten.
Kalpa-APIn tarkastelua varten selaimessa kannattaa olla asennettuna JSON-lis&auml;osa. Huom! Isoja tietolajeja (esim. tielinkit, nopeusrajoitus) Kalpa-APIsta tarkastellessa voi tietojen latautuminen selaimeen kest&auml;&auml; todella kauan tai selain voi my&ouml;s kaatua.

Kalpa-APIn osoite on (Liikenneviraston verkon ulkopuolelta)

Testikannassa: https://devtest.liikennevirasto.fi/digiroad/api/integration/tietolaji_tahan?municipality=kuntanumero_tahan
Koulutuksessa: https://apptest.liikennevirasto.fi/digiroad/api/integration/tietolaji_tahan?municipality=kuntanumero_tahan
Tuotannossa: https://testiextranet.liikennevirasto.fi/digiroad/api/integration/tietolaji_tahan?municipality=kuntanumero_tahan

Esim: https://devtest.liikennevirasto.fi/digiroad/api/integration/mass_transit_stops?municipality=5 (pys&auml;kit kunnasta 5)

![Kalpa API.](k160.JPG)

_Pys&auml;kki Kalpa-APIssa._

__Tietolajit Kalpa-Apissa:__

Tielinkki road_link_properties
K&auml;&auml;ntymisrajoitus manoeuvres
Pys&auml;kki	mass_transit_stops
Nopeusrajoitus speed_limits
Suurin sallittu massa total_weight_limits
Yhdistelm&auml;n suurin sallittu massa trailer_truck_weight_limits
Suurin sallittu akselimassa axle_weight_limits
Suurin sallittu telimassa bogie_weight_limits
Suurin sallittu korkeus height_limits
Suurin sallittu pituus length_limits
Suurin sallittu leveys width_limits
Valaistu tie lit_roads
P&auml;&auml;llystetty tie paved_roads
Ajoneuvokohtainen rajoitus vehicle_prohibitions
VAK-rajoitus hazardous_material_transport_prohibitions
Leveys widths
Liikennem&auml;&auml;r&auml; traffic_volumes
Ruuhkaantumisherkkyys congestion_tendencies
Kelirikko roads_affected_by_thawing
Talvinopeusrajoitus	speed_limits_during_winter
Kaistojen lukum&auml;&auml;r&auml;	number_of_lanes
Joukkoliikennekaista mass_transit_lanes
Suojatie pedestrian_crossings
Esterakennelma obstacles
Rautatien tasoristeys railway_crossings
Opastustaulu ja sen informaatio	directional_traffic_signs

Jokaisen ymp&auml;rist&ouml;n Kalpa-APIin on oma salasanansa. Salasanat voi kysy&auml; Digiroad2-kehitystiimilt&auml;. K&auml;ytt&auml;j&auml;tunnus on kaikkiin sama (kalpa).

Liikenneviraston sis&auml;verkossa tai SSH-yhteyden kautta Kalpa-APIa voi k&auml;ytt&auml;&auml; IP-osoitteiden avulla, jolloin ei tarvitse erikseen autentikoitua Livin extranet-tunnuksilla. (IP-osoitteet voisi lis&auml;t&auml;, mutta operaattori ei toistaiseksi tarvitse n&auml;it&auml;)

Tietokannasta ominaisuustiedot siirtyv&auml;t Kalpa-APIin sellaisenaan. Kalpa-APIsta tiedot k&auml;ytet&auml;&auml;n exportteihin p&auml;&auml;asiassa tietokannasta tulevassa muodossa. T&auml;h&auml;n on kuitenkin muutamia poikkeuksia, jotta tietojen hy&ouml;dynt&auml;minen olisi hy&ouml;dynt&auml;jille helpompaa. 

Muutokset tehd&auml;&auml;n exporttien FME-ty&ouml;tilassa.

1. Suuntatiedollisten pisteiden astesuunnan eli suuntiman korjaus (esim. pys&auml;kit): Pisteen suunta on tallennettu tietokantaan suhteessa digitointisuuntaan. Lis&auml;ksi pisteell&auml; on tallennettuna tielinkin kaltevuudesta suunta astelukuna. N&auml;iden kahden yhdistelm&auml;ll&auml; voidaan laskea pisteen todellinen suuntima. Ilman korjausta suuntimaa ei voi hy&ouml;dynt&auml;&auml; ilman digitointisuuntaa. Exportteihin vied&auml;&auml;n todellinen suuntima (asteina 0-360):
	a. Jos pisteen vaikutussuunta on digitointisuuntaan (=2): asteluku eli suuntima on tietokantaan tallennettu (ei korjauksia)
	b. Jos pisteen vaikutussuunta on digitointisuuntaa vastaan (=3): jos suuntima <= 180, niin suuntima on suuntima+180, esim. 175+180=355 ja jos suuntima on > 180, niin suuntima on suuntima-180, eli esim. 267-180=84

2. Kaikille kohteille on lis&auml;tty kuntanumero (tielinkin kuntanumeron perusteella)

3. Pys&auml;kin ensimm&auml;inen ja viimeinen voimassaolop&auml;iv&auml;m&auml;&auml;r&auml; on muutettu pp.kk.vvvv -muotoon, jotta kaikki exporttien p&auml;iv&auml;m&auml;&auml;r&auml;t olisivat samassa muodossa


10. Kuntaliitosten aiheuttamat muutokset vuodenvaihteessa
-------------------

Vuodenvaihteessa tapahtuvat kuntaliitokset aiheuttavat muutoksia sek&auml; OTH:ssa ett&auml; VVH:ssa. VVH:ssa p&auml;ivitet&auml;&auml;n tielinkkien kuntakoodit vastaamaan uutta tilannetta. OTH:ssa on pisteille tallennettu kuntakoodi tietokantaan, mink&auml; vuoksi nuo kuntakoodit tulee korjata vastaamaan uutta tilannetta tietokannassa. 

Seuraavat muutokset tulee huomioida kuntaliitosten yhteydess&auml;:

1. VVH p&auml;ivitt&auml;&auml; kuntakoodit -> Kun VVH:ssa p&auml;ivitet&auml;&auml;n kuntakoodit, muuttuvat OTH:n pistem&auml;iset kohteet kelluviksi
2. OTH:ssa p&auml;ivitet&auml;&auml;n asset-tauluun kuntakoodit tietokantaan -> kohteet eiv&auml;t kellu en&auml;&auml;
3. K&auml;ytt&auml;jien k&auml;ytt&ouml;oikeudet uusien kuntanumeroiden mukaisiksi OTH:n tuotantokannassa
4. Turhien kuntien poistaminen OTH:n kunta- ja ely-tauluista tietokannasta

Kuntaliitoksiin liittyv&auml;t muutokset on muistettava tehd&auml; kaikkiin ymp&auml;rist&ouml;iihin (testikanta, koulutuskanta ja tuotantokanta)! Muutokset kannattaa tehd&auml; ensin VVH:n testiin ja OTH:n testiin, ja sen j&auml;lkeen VVH:n tuotantokantaan ja sitten OTH:n koulutuskantaan ja tuotantokantaan.

Linkit:
------

[Loppuk&auml;ytt&auml;j&auml;n ohje](https://testiextranet.liikennevirasto.fi/digiroad/manual)
 
[L&auml;hdekoodi](https://github.com/finnishtransportagency/digiroad2)


Yhteystiedot
------------

__Digiroadin kehitystiimi:__

digiroad2@reaktor.fi

__Palaute operaattorin manuaalista:__

emmi.sallinen@karttakeskus.fi