Viite-sovelluksen k&auml;ytt&ouml;ohje
======================================================

__Huom! Suosittelemme Firefoxia tai Chromea, kun sovelluksella yll&auml;pidet&auml;&auml;n Digiroad-tietoja.__

__Huom! K&auml;ytt&ouml;ohjeen kuvia voi klikata isommaksi, jolloin tekstit erottuvat paremmin.__

1. Miten p&auml;&auml;st&auml; alkuun?
-----------------------

Viite-sovelluksen k&auml;ytt&ouml;&auml; varten tarvitaan Liikenneviraston tunnukset (A-, U-, LX-, K- tai L-alkuinen). Mik&auml;li sinulla ei ole tunnuksia, pyyd&auml; ne yhteyshenkil&ouml;lt&auml;si Liikennevirastosta.

Kaikilla Liikenneviraston tunnuksilla on p&auml;&auml;sy Viite-sovellukseen.

Viite-sovellukseen kirjaudutaan osoitteessa: <a href=https://devtest.liikennevirasto.fi/viite/ target="_blank">https://devtest.liikennevirasto.fi/viite/. </a>

![Kirjautuminen Viite-sovellukseen.](k1.JPG)

_Kirjautuminen Viite-sovellukseen._

Kirjautumisen j&auml;lkeen avautuu karttak&auml;ytt&ouml;liittym&auml;ss&auml; katselutila.

![N&auml;kym&auml; kirjautumisen j&auml;lkeen.](k2.JPG)

_Karttan&auml;kym&auml; kirjautumisen j&auml;lkeen._

Oikeudet on rajattu maantieteellisesti sek&auml; k&auml;ytt&auml;j&auml;n roolin mukaan.

- Ilman erikseen annettuja oikeuksia Liikenneviraston tunnuksilla p&auml;&auml;see katselemaan kaikkia tieosoitteita
- Sovelluksen k&auml;ytt&auml;j&auml;ll&auml; on oikeudet muokata h&auml;nelle m&auml;&auml;riteltyjen Elyjen maantieteellisten kuntarajojen sis&auml;puolella olevia tieosoitteita
- Joillekin k&auml;ytt&auml;jille on voitu antaa oikeudet koko Suomen alueelle
- Tieosoiteprojektit -painike ja Siirry muokkaustilaan -painike n&auml;kyv&auml;t vain k&auml;ytt&auml;jille, joilla on oikeudet muokata tieosoitteita

Jos kirjautumisen j&auml;lkeen ei avaudu karttak&auml;ytt&ouml;liittym&auml;n katselutilaa, ei kyseisell&auml; tunnuksella ole p&auml;&auml;sy&auml; Liikenneviraston extranettiin. T&auml;ll&ouml;in tulee ottaa yhteytt&auml; Liikennevirastossa omaan yhteyshenkil&ouml;&ouml;n.

1.1 Mist&auml; saada opastusta?
--------------------------

Viite-sovelluksen k&auml;yt&ouml;ss&auml; avustaa vuoden 2016 loppuun asti Emmi Sallinen, emmi.sallinen@karttakeskus.fi

####Ongelmatilanteet####

Sovelluksen toimiessa virheellisesti (esim. kaikki aineistot eiv&auml;t lataudu oikein) toimi seuraavasti:

- Lataa sivu uudelleen n&auml;pp&auml;imist&ouml;n F5-painikkeella.
- Tarkista, ett&auml; selaimestasi on k&auml;yt&ouml;ss&auml; ajan tasalla oleva versio ja selaimesi on Mozilla Firefox tai Chrome
- Jos edell&auml; olevat eiv&auml;t korjaa ongelmaa, ota yhteytt&auml; emmi.sallinen@karttakeskus.fi


2. Perustietoja Viite-sovelluksesta
--------------------------

_T&auml;h&auml;n joku hieno kuva sitten, kun esim. liittym&auml;t tierekisteriin on tarkemmin tiedossa._

2.1 Viite-sovelluksen yhteydet muihin j&auml;rjestelmiin
--------------------------

###V&auml;yl&auml;verkon hallinta (VVH)###

Viite-sovelluksessa pohja-aineistona oleva geometria tulee Liikenneviraston V&auml;yl&auml;verkon hallinnan (VVH) sovelluksessa. VVH-sovellukseen ladataan p&auml;ivitt&auml;in Maanmittauslaitokselta p&auml;ivitykset Maastotietokannan (MTK) keskilinja-aineistoon, jolloin my&ouml;s Viite-sovelluksessa on k&auml;yt&ouml;ss&auml; uusi keskilinjageometria joka p&auml;iv&auml;.

###Digiroad: Ominaisuustietojen hallinta###

Molemmissa sovelluksissa on k&auml;yt&ouml;ss&auml; sama, V&auml;yl&auml;verkon hallinnan tarjoama keskilinjageometria.

Lis&auml;ksi OTH-sovellus hy&ouml;dynt&auml;&auml; Viitteen tieosoitetietoja, jotka n&auml;ytet&auml;&auml;n OTH:ssa tielinkin ominaisuustietona.

###Liikenneviraston Tierekisteri###

_T&auml;ydennet&auml;&auml;n my&ouml;hemmin..._

2.2 Tiedon rakentuminen Viite-sovelluksessa
--------------------------

Viite-sovelluksessa tieosoiteverkko piirret&auml;&auml;n VVH:n tarjoaman Maanmittauslaitoksen keskilinja-aineiston p&auml;&auml;lle. Maanmittauslaitoksen keskilinja-aineisto muodostuu tielinkeist&auml;. Tielinkki on tien, kadun, kevyen liikenteen v&auml;yl&auml;n tai lauttayhteyden keskilinjageometrian pienin yksikk&ouml;. Tieosoiteverkko piirtyy geometrian p&auml;&auml;lle tieosoitesegmenttein&auml; _lineaarisen referoinnin_ avulla. 

Tielinkki on Viite-sovelluksen lineaarinen viitekehys, eli sen geometriaan sidotaan tieosoitesegmentit. Kukin tieosoitesegmentti tiet&auml;&auml; mille tielinkille se kuuluu (tielinkin ID) sek&auml; kohdan, josta se alkaa ja loppuu kyseisell&auml; tielinkill&auml;. Tieosoitesegmentit ovat siten tielinkin mittaisia tai niit&auml; lyhyempi&auml; tieosoitteen osuuksia.

Kullakin tieosoitesegmentill&auml; on lis&auml;ksi tiettyj&auml; sille annettuja ominaisuustietoja, kuten tienumero, tieosanumero ja ajoratakoodi. Tieosoitesegmenttien ominaisuustiedoista on kerrottu tarkemmin kohdassa "Tieosoitteen ominaisuustiedot".

![Kohteita](k9.JPG)

_Tieosoitesegmenttej&auml; (1) ja muita tielinkkej&auml; (2) Viitteen karttaikunnassa._

Tieosoitesegmentit piirret&auml;&auml;n Viite-sovelluksessa kartalle erilaisin v&auml;rein (kts. kohta 4. Tieosoiteverkon katselu). Muut tielinkit, jotka eiv&auml;t kuulu tieosoiteverkkoon, piirret&auml;&auml;n kartalle harmaalla. N&auml;it&auml; ovat esimerkiksi tieosoitteettomat kuntien omistamat tiet, ajopolut, ajotiet jne. pienemm&auml;t tieosuudet.

Palautteet geometrian eli tielinkkien virheist&auml; voi laittaa Maanmittauslaitokselle, maasto@maanmittauslaitos.fi. Mukaan selvitys virheest&auml; ja sen sijainnista (esim. kuvakaappaus).

3. Karttan&auml;kym&auml;n muokkaus
--------------------------

![Karttan&auml;kym&auml;n muokkaus](k3.JPG)

_Karttan&auml;kym&auml;._

####Kartan liikutus####

Karttaa liikutetaan raahaamalla.

####Mittakaavataso####

Kartan mittakaavatasoa muutetaan joko hiiren rullalla, tuplaklikkaamalla, Shift+piirto (alue) tai mittakaavapainikkeista (1). Mittakaavapainikkeita k&auml;ytt&auml;m&auml;ll&auml; kartan keskitys s&auml;ilyy. Hiiren rullalla, tuplaklikkaamalla tai Shift+piirto (alue) kartan keskitys siirtyy kursorin kohtaan.  K&auml;yt&ouml;ss&auml; oleva mittakaavataso n&auml;kyy kartan oikeassa alakulmassa (2).

####Kohdistin####

Kohdistin (3) kertoo kartan keskipisteen. Kohdistimen koordinaatit n&auml;kyv&auml;t karttaikkunan oikeassa alakulmassa(4). Kun kartaa liikuttaa eli keskipiste muuttuu, p&auml;ivittyv&auml;t koordinaatit. Oikean alakulman valinnan (5) avulla kohdistimen saa my&ouml;s halutessaan piilotettua kartalta.

####Merkitse piste kartalla####

Merkitse-painike (6) merkitsee sinisen pisteen kartan keskipisteeseen. Merkki poistuu vain, kun merkit&auml;&auml;n uusi piste kartalta.

####Taustakartat####

Taustakartaksi voi valita vasemman alakulman painikkeista maastokartan, ortokuvat tai taustakarttasarjan. K&auml;yt&ouml;ss&auml; on my&ouml;s harmaas&auml;vykartta (t&auml;m&auml;n hetken versio ei kovin k&auml;ytt&ouml;kelpoinen).

####Hakukentt&auml;####

K&auml;ytt&ouml;liittym&auml;ss&auml; on hakukentt&auml; (8), jossa voi hakea koordinaateilla ja katuosoitteella tai tieosoitteella. Haku suoritetaan kirjoittamalla hakuehto hakukentt&auml;&auml;n ja klikkaamalla Hae. Hakutulos tulee listaan hakukent&auml;n alle. Hakutuloslistassa ylimp&auml;n&auml; on maantieteellisesti kartan nykyist&auml; keskipistett&auml; l&auml;himp&auml;n&auml; oleva kohde. Mik&auml;li hakutuloksia on vain yksi, keskittyy kartta automaattisesti haettuun kohteeseen. Jos hakutuloksia on useampi kuin yksi, t&auml;ytyy listalta valita tulos, jolloin kartta keskittyy siihen. Tyhjenn&auml; tulokset -painike tyhjent&auml;&auml; hakutuloslistan.

Koordinaateilla haku: Koordinaatit sy&ouml;tet&auml;&auml;n muodossa "pohjoinen (7 merkki&auml;), it&auml; (6 merkki&auml;)". Koordinaatit tulee olla ETRS89-TM35FIN -koordinaattij&auml;rjestelm&auml;ss&auml;. Esim. 6975061, 535628.

Katuosoitteella haku: Katuosoitteesta hakukentt&auml;&auml;n voi sy&ouml;tt&auml;&auml; koko ositteen tai sen osan. Esim. "Mannerheimintie" tai "Mannerheimintie 10, Helsinki".

Tieosoitteella haku: Tieosoitteesta hakukentt&auml;&auml;n voi sy&ouml;tt&auml;&auml; koko osoitteen tai osan siit&auml;. Esim. 2 tai 2 1 150. (Varsinainen tieosoitehaku tieosoitteiden yll&auml;pidon tarpeisiin toteutetaan my&ouml;hemmin)


4. Tieosoiteverkon katselu
--------------------------

Tieosoiteverkko tulee n&auml;kyviin, kun zoomaa tasolle, jossa mittakaavajanassa on 2 km. T&auml;st&auml; tasosta ja sit&auml; l&auml;hemp&auml;&auml; piirret&auml;&auml;n kartalle valtatiet, kantatiet, seututiet, yhdystiet ja numeroidut kadut. Kun zoomaa tasolle, jossa mittakaavajanassa on suurempi 100 metri&auml; (100 metrin mittakaavajanoja on kaksi kappaletta), tulevat n&auml;kyviin kaikki tieverkon kohteet.

Tieosoiteverkko on v&auml;rikoodattu tienumeroiden mukaan. Vasemman yl&auml;kulman selitteess&auml; on kerrottu kunkin v&auml;rikoodin tienumerot. Lis&auml;ksi kartalle piirtyv&auml;t kalibrointipisteet, eli ne kohdat, joissa vaihtuu tieosa tai ajoratakoodi. Tieverkon kasvusuunta n&auml;kyy kartalla pisaran mallisena nuolena.

![Mittakaavajanassa 2km](k4.JPG)

_Mittakaavajanassa 2 km._

![Mittakaavajanassa 100 m](k5.JPG)

_Mittakaavajanassa 100 m._

Tieosoitteelliset kadut erottuvat kartalla muista tieosoitesegmenteist&auml; siten, ett&auml; niiden ymp&auml;rill&auml; on musta v&auml;ritys.

![Tieosoitteellinen katu](k16.JPG)

_Tieosoitteellinen katu, merkattuna mustalla v&auml;rityksell&auml; tienumeron v&auml;rityksen lis&auml;ksi._

Kun hiiren vie tieosoiteverkon p&auml;&auml;lle, tulee kartalle n&auml;kyviin "infolaatikko", joka kertoo kyseisen tieosoitesegmentin tienumeron, tieosanumeron, ajoratakoodin, alkuet&auml;isyyden ja loppuet&auml;isyyden.

![Hover](k35.JPG)

_Infolaatikko, kun hiiri on viety tieosoitesegmentin p&auml;&auml;lle._

4.1 Kohteiden valinta
--------------------------
Kohteita voi valita klikkaamalla kartalta. Klikkaamalla kerran, sovellus valitsee kartalta ruudulla n&auml;kyv&auml;n osuuden kyseisest&auml; tieosasta, eli osuuden jolla on sama tienumero, tieosanumero ja ajoratakoodi. Valittu tieosa korostuu kartalla (1), ja sen tiedot tulevat n&auml;kyviin karttaikkunan oikeaan laitaan ominaisuustieton&auml;kym&auml;&auml;n (2).

![Tieosan valinta](k6.JPG)

_Tieosan valinta._

Tuplaklikkaus valitsee yhden tieosoitesegmentin. Tieosoitesegmentti on tielinkin mittainen tai sit&auml; lyhyempi osuus tieosoitetta. Valittu tieosoitesegmentti korostuu kartalla (3), ja sen tiedot tulevat n&auml;kyviin karttaikkunan oikeaan laitaan ominaisuustieton&auml;kym&auml;&auml;n (4).

![Tieosoitesegmentin valinta](k7.JPG)

_Tieosoitesegmentin valinta._


##Tieosoitteen ominaisuustiedot##

Tieosoitteilla on seuraavat ominaisuustiedot:

|Ominaisuustieto|Kuvaus|Sovellus muodostaa|
|---------------|------|----------------|
|Segmentin ID|Segmentin yksil&ouml;iv&auml; ID, n&auml;kyy kun tieosoitteen valitsee tuplaklikkaamallaID|X|
|Muokattu viimeksi*|Muokkaajan k&auml;ytt&auml;j&auml;tunnus ja tiedon muokkaushetki.|X|
|Linkkien lukum&auml;&auml;r&auml;|Niiden tielinkkien lukum&auml;&auml;r&auml;, joihin valinta  kohdistuu.|X|
|Tienumero|Tieosoiteverkon mukainen tienumero. L&auml;ht&ouml;aineistona Tierekisterin tieosoitteet vuonna 2016.||
|Tieosanumero|Tieosoiteverkon mukainen tieosanumero. L&auml;ht&ouml;aineistona Tierekisterin tieosoitteet vuonna 2016.||
|Ajorata|Tieosoiteverkon mukainen ajoratakoodi. L&auml;ht&ouml;aineistona Tierekisterin tieosoitteet vuonna 2016.||
|Alkuet&auml;isyys**|Tieosoiteverkon kalibrointipisteiden avulla laskettu alkuet&auml;isyys. Kalibrointipisteen kohdalla alkuet&auml;isyyden l&auml;ht&ouml;aineistona on Tierekisterin tieosoitteet vuonna 2016.|X|
|Loppuet&auml;isyys**|Tieosoiteverkon kalibrointipisteiden avulla laskettu loppuet&auml;isyys. Kalibrointipisteen kohdalla loppuet&auml;isyyden l&auml;ht&ouml;aineistona on Tierekisterin tieosoitteet vuonna 2016.|X|
|ELY|Liikenneviraston ELY-numero.|X|
|Tietyyppi|Muodostetaan Maanmittauslaitoksen hallinnollinen luokka -tiedoista, kts. taulukko alempana. Jos valitulla tieosalla on useita tietyyppej&auml;, ne kerrotaan ominaisuustietotaulussa pilkulla erotettuna.|X|
|Jatkuvuus|Tieosoiteverkon mukainen jatkuvuus-tieto. L&auml;ht&ouml;aineistona Tierekisterin tieosoitteet vuonna 2016.|X|

*)Muokattu viimeksi -tiedoissa vvh_modified tarkoittaa, ett&auml; muutos on tullut Maanmittauslaitokselta joko geometriaan tai geometrian ominaisuustietoihin. Muokatti viimeksi -p&auml;iv&auml;t ovat kaikki v&auml;hint&auml;&auml;n 29.10.2015, koska tuolloin on tehty Maanmittauslaitoksen geometrioista alkulataus VVH:n tietokantaan.
**)Tieosoiteverkon kalibrointipisteet (tieosan alku- ja loppupisteet sek&auml; ajoratakoodin vaihtuminen) m&auml;&auml;rittelev&auml;t mitatut alku- ja loppuet&auml;isyydet. Kalibrointipiste v&auml;lill&auml; alku- ja loppuet&auml;isyydet lasketaan tieosoitesegmenttikohtaisesti Viite-sovelluksessa.

__Tietyypin muodostaminen Viite-sovelluksessa__

J&auml;rjestelm&auml; muodostaa tietyyppi-tiedon automaattisesti Maanmittauslaitoksen aineiston pohjalta seuraavalla tavalla:

|Tietyyppi|Muodostamistapa|
|---------|---------------|
|1 Maantie|MML:n hallinnollinen luokka arvolla 1 = Valtio|
|2 Lauttav&auml;yl&auml; maantiell&auml;|MML:n hallinnollinen luokka arvolla 1 = Valtio ja MML:n kohdeluokka arvolla lautta/lossi|
|3 Kunnan katuosuus|MML:n hallinnollinen luokka arvolla 2 = Kunta|
|5 Yksityistie|MML:n hallinnollinen luokka arvolla 3 = Yksityinen|
|9 Ei tiedossa|MML:lla ei tiedossa hallinnollista luokkaa|

Palautteet hallinnollisen luokan virheist&auml; voi toimittaa Maanmittauslaitokselle osoitteeseen maasto@maanmittauslaitos.fi. Mukaan selvitys virheest&auml; ja sen sijainnista (kuvakaappaus tms.).

##Kohdistaminen tieosoitteeseen tielinkin ID:n avulla##

Kun kohdetta klikkaa kartalla, tulee selaimen osoiteriville n&auml;kyviin valitun kohteen tielinkin ID. Osoiterivill&auml; olevan URL:n avulla voi my&ouml;s kohdistaa k&auml;ytt&ouml;liittym&auml;ss&auml; ko. tielinkkiin. URL:n voi esimerkiksi l&auml;hett&auml;&auml; toiselle henkil&ouml;lle s&auml;hk&ouml;postilla, jolloin h&auml;n p&auml;&auml;see samaan paikkaan k&auml;ytt&ouml;liittym&auml;ss&auml; helposti.

Esimerkiksi: https://devtest.liikennevirasto.fi/viite/#linkProperty/1747227 n&auml;kyy kuvassa osoiterivill&auml; (5). 1747227 on tielinkin ID (eri asia, kuin segmentin ID, jota ei voi k&auml;ytt&auml;&auml; hakemiseen).

![Kohdistaminen tielinkin ID:ll&auml;](k8.JPG)

_Kohdistaminen tielinkin ID:ll&auml;._

5. Tuntemattomat tieosoitesegmentit
--------------------------

Tuntemattomilla tieosoitesegmenteill&auml; tarkoitetaan tieosoitesegmenttej&auml;, joilla pit&auml;isi olla tieosoite, mutta niill&auml; ei ole sit&auml;. Puuttuva tieosoite voi johtua esimerkiksi siit&auml;, ett&auml; Maanmittauslaitoksen tekem&auml;t geometriap&auml;ivitykset ovat aiheuttaneet tielinkki-aineistossa niin suuria muutoksia, ettei tieosoitesegmentti&auml; saada en&auml;&auml; sovitettua p&auml;ivittyneen tielinkin p&auml;&auml;lle.

Tuntemattomaksi tieosoitesegmentiksi katsotaan kohteet, jotka ovat: 

1. Maanmittauslaitoksen hallinnollisessa luokittelussa arvolla 1 = Valtion omistama tie

	- N&auml;m&auml; kohteet muodostavat suurimman osan tieosoiteverkosta

1. Sellaisessa sijainnissa, ett&auml; siin&auml; on aiemmin ollut voimassaoleva tieosoite

	- N&auml;m&auml; kohteet ovat esimerkiksi tieosoitteellisia katuja (maanmittauslaitoksen hallinnollinen luokka arvolla 2 = Kunnan omistama)

Tuntemattomat tieosoitesegmentit visualisoidaan kartalle mustalla viivalla ja kysymysmerkill&auml; (1).

![Tuntematon tieosoitesegmentti](k10.JPG)

_Tuntemattomia tieosotiesegmenttej&auml;._

Tuntemattomia tieosoitesegmenttej&auml; voi valita ja tarkastella klikkailemalla samalla tavalla, kuin muitakin tieosoitesegmenttej&auml;. Tuntemattoman tieosoitesegmentin tiedot ominaisuustietotaulussa (2) ovat puutteelliset, koska ko. kohdasta puuttuu tiedot tieosoitteesta.

![Tuntemattoman tieosoitesegmentin puuttelliset ominaisuustiedot](k11.JPG)

_Tuntematon tieosoitesegmentti valittuna. Ominaisuustietotaulun tiedot ovat puuttelliset._

6. Rakenteilla olevat tielinkit ja niiden tieosoitteet
--------------------------

Rakenteilla olevilla kohteilla tarkoitetaan sellaisia tielinkkej&auml;, joiden valmiusaste/status on Maanmittauslaitoksella "Rakenteilla". 

Rakenteilla olevat, tieosoitteistettavat kohteet n&auml;kyv&auml;t k&auml;ytt&ouml;liittym&auml;ss&auml; oranssi-mustalla raidoituksella (1). N&auml;ill&auml; kohteilla on hallinnollisen luokan arvo 1=Valtion omistama, mutta niilt&auml; puuttuu tieosoite eli kohteet ovat tuntemattomia. Tuntemattomilla, rakenteilla olevilla kohteilla on my&ouml;s musta kysymysmerkki-label kohteen p&auml;&auml;ll&auml;.

![Tuntemattoman rakenteilla oleva kohde](k13.JPG)

_Tuntematon rakenteilla oleva kohde._

Jos rakenteilla olevalle kohteelle on jo annettu tieosoite, se n&auml;kyy k&auml;ytt&ouml;liittym&auml;ss&auml; kuten muutkin tieosoitteistetut tielinkit (3) ja tieosoitetiedot n&auml;kyv&auml;t my&ouml;s ominaisuustietotaulussa (4).

![Tieosoitteistettu rakenteilla oleva kohde](k15.JPG)

_Pisaraliittym&auml; on rakenteilla, mutta koska se on jo saanut tieosoiteen, se n&auml;kyy k&auml;ytt&ouml;liittym&auml;ss&auml; kuten muutkin tieosoitteistetut tielinkit._

Muut rakenteilla olevat kohteet n&auml;kyv&auml;t k&auml;ytt&ouml;liittym&auml;ss&auml; harmaa-mustalla raidoituksella (2). N&auml;iden hallinnollisen luokan arvo on jotain muuta, kuin 1=Valtion omistama.

![Rakenteilla oleva kohde](k14.JPG)

_Rakenteilla oleva kohde, jonka hallinnollisen luokan arvo on jokin muu kuin 1=Valtion omistama._

7. Geometriasta irti olevat tieosoitesegmentit
--------------------------
Geometriasta irti olevilla tieosoitesegmenteilla tarkoitetaan sellaisia segmentteja jotka eiv&auml;t l&ouml;yd&auml; omaa tielinkin ID:ta vastaavaa lineaarilokaatiota k&auml;yt&ouml;ss&auml; olevista linkeist&auml;.

Geometriasta irti olevan segmentin tunnistaa keltaisesta korostuksesta. Lis&auml;ksi jokaisella tieosoitesegmentilla on punainen huomiolippu. Segmentin voi valita huomiolippua tai keltaista korostusta klikkaamalla (1).  

![Geometriasta irti oleva tieosoitesegmentti](k12.JPG)

_Geometriasta irti olevia tieosoitesegmenttej&auml;. Segmentin tiedot saa n&auml;kyviin klikkaamalla segmentin keltaista korostetta tai punaista huomiolippua._

Keltaisten kohteiden valinta toimii yksinkertaisemmin, kuin muiden tieosoitesegmenttien. Sek&auml; klikkaamalla kerran ett&auml; tuplaklikkaus valitsevat molemmat ruudulta n&auml;kyv&auml;n osuuden kyseisest&auml; tieosasta, eli osuuden jolla on sama tienumero, tieosanumero ja ajoratakoodi. T&auml;m&auml; sen vuoksi, ett&auml; kohteiden korjaaminen sujuu tehokkaammin, kun valinta tapahtuu aina koko keltaiselle osuudelle tieosasta. Kohdetta valitessa kannattaa olla huolellinen, ett&auml; klikatessa osuu keltaiseen korostukseen eik&auml; tuntemattomaan tielinkkiin (musta kohde). 

Valitun kohteen tiedot tulevat n&auml;kyviin ominaisuustieton&auml;kym&auml;&auml;n. Geometriasta irti olevalla kohteella on ominaisuustieton&auml;kym&auml;ss&auml; huomiolaatikko joka varoittaa tien geometrian muuttuneen (2).

8. Geometriasta irti olevien tieosoitesegmenttien korjaaminen takaisin geometrialle
--------------------------

Geometriasta irti olevien tieosoitesegmenttien korjausta varten k&auml;ytt&auml;j&auml;n tulee siirty&auml; muokkaustilaan. Korjausprosessissa geometriasta irti olevat tieosoitesegmentit kiinnitet&auml;&auml;n takaisin geometriaan kertomalla niille uudet tielinkit, joihin ne kiinnittyv&auml;t.

Yleispiirteinen korjausprosessi: ensin valitaan kartalta keltainen, geometriasta irti oleva tieosoitesegmentti. T&auml;m&auml;n j&auml;lkeen sovellus ohjeistaa k&auml;ytt&auml;j&auml;&auml; n&auml;yt&ouml;n oikean laidan ominaisuustietotaulussa jatkamaan valintoja tai painamaan Valinta valmis -painiketta. Muutokset voi tarkistaa Siirr&auml;-painikkeen painamisen j&auml;lkeen ennen Tallennusta.

__Korjausprosessi tarkemmin:__


__Vaihe 1__

Valitaan keltainen tieosoitesegmentti klikkaamalla keltaisesta osuudesta tai klikkaamalla punaisesta huomiolipusta. Valitun kohteen tiedot tulevat n&auml;kyviin ominaisuustietotauluun ja valittu kohde korostuu kartalla (1). 

Muokkaustilassa keltaisten, geometriasta irti olevien tieosoitesegmenttien valinnan poistaminen tehd&auml;&auml;n aina ominaisuustietotaulun oikean alalaidan Peruuta-painikkeen (2) kautta.

__Vaihe 2__

Tarkistetaan ominaisuustietotaulusta, onko kohteen vieress&auml; lis&auml;&auml; valittavia korjattavia tieosoitesegmenttej&auml; (3) (t&auml;ss&auml; tapauksessa ei ole). Jos on tarpeen valita lis&auml;&auml; kohteita, klikataan niit&auml; ominaisuustietotaulun Valitse-painikkeista. Kun kaikki tarpeelliset valinnat on tehty, klikataan Valinta valmis -painiketta (4).

![korjaus](k27.JPG)


__Vaihe 3__

Kun Valinta valmis -painiketta on painettu, sovellus muuttaa piirtoj&auml;rjestyksen sitten, ett&auml; mustat tuntemattomat kohteet piirtyv&auml;t p&auml;&auml;limm&auml;iseksi. 

![korjaus](k28.JPG)

Seuraavaksi valitaan klikkaamalla tuntematon musta tielinkki, jolle kohde halutaan siirt&auml;&auml;. Valinnan j&auml;lkeen sovellus muuttaa valitun tuntemattoman mustan kohteen vihre&auml;ksi, eli valituksi.

![korjaus](k29.JPG)

__Vaihe 4__

Tarkistetaan ominaisuustietotaulusta (6), onko kohteen vieress&auml; lis&auml;&auml; valittavia mustia tuntemattomia tieosoitesegmenttej&auml;, joille valitut keltaiset tieosoitesegmentit pit&auml;isi siirt&auml;&auml;. Jos on tarpeen valita lis&auml;&auml; kohteita, klikataan niit&auml; ominaisuustietotaulun Valitse-painikkeesta.

![korjaus](k30.JPG)

Kaikki valitut linkit listataan ominaisuustietotaulussa (7) ja ne n&auml;kyv&auml;t kartalla vihre&auml;n&auml; (8). Kun kaikki tarpeelliset valinnat on tehty, klikataan oranssia Siirr&auml;-painiketta (9).

![korjaus](k31.JPG)

__Vaihe 5__

Siirron j&auml;lkeen sovellus pyyt&auml;&auml; (1) k&auml;ytt&auml;j&auml;&auml; tarkastamaan tuloksen kartalta. Jos tulos on ok, painetaan Tallenna (2). Jos k&auml;ytt&auml;j&auml; haluaa tehd&auml; muutoksia valintoihin, painetaan Peruuta (3).

![korjaus](k32.JPG)

__Vaihe 6__

Korjauksen j&auml;lkeen tieosoiteverkko on ehe&auml;. Tallentamisen j&auml;lkeen tieosoitesegmenttien kalibrointipisteet ja kalibrointipisteiden kohdalla olevat alku- ja loppuet&auml;isyysarvot ovat s&auml;ilyneet ennallaan. Kohteet ovat vain saaneet uudet tielinkit, joihin niiden lineaarilokaatio on kiinnitetty.

![korjaus](k33.JPG)


8.1 Siirron ep&auml;onnistuminen
--------------------------

Jos vaiheen 4 siirto ei onnistu, sovellus ilmoittaa t&auml;st&auml; k&auml;ytt&auml;j&auml;lle. T&auml;ll&ouml;in taustaj&auml;rjestelm&auml; ei osaa siirt&auml;&auml; tieosoitesegmenttej&auml; uudelle geometrialle, eik&auml; siirron tietoja tallenneta tietokantaan. N&auml;m&auml; kohteet voi j&auml;tt&auml;&auml; toistaiseksi korjaamatta, ja k&auml;ytt&auml;j&auml;n tulisi ilmoittaa niist&auml; j&auml;rjestelm&auml;tukeen, jotta sovelluksen toimintaa voidaan kehitt&auml;&auml;.

![korjaus](k34.JPG)

_Ilmoitus siirron ep&auml;onnistumisesta._

Ep&auml;onnistuneen siirron j&auml;lkeen ty&ouml;skentely&auml; voi jatkaa toisella kohteella klikkaamalla Peruuta-painiketta kaksi kertaa.


9. Tieosoiteprojektin tekeminen
--------------------------

Uuden tieosoiteprojektin tekeminen aloitetaan klikkaamalla vasemman yl&auml;kulman painiketta Tieosoiteprojektit (1) ja avautuvasta ikkunasta painiketta Uusi tieosoiteprojekti (2).

![Uusi tieosoiteprojekti](k17.JPG)

_Tieosoiteprojektit -painike ja Uusi tieosoiteprojekti -painike._

N&auml;yt&ouml;n oikeaan laitaan avautuu lomake tieosoiteprojektin perustietojen t&auml;ydent&auml;miseen. Jos k&auml;ytt&auml;j&auml; ei ole muokkaustilassa, sovellus siirtyy t&auml;ss&auml; vaiheessa automaattisesti muokkaustilaan.

![Uusi tieosoiteprojekti](k18.JPG)

_Tieosoiteprojektin perustietojen lomake._

Pakollisia tietoja ovat nimi ja projektin muutosten voimaantulop&auml;iv&auml;m&auml;&auml;r&auml;, jotka on merkattu lomakkeelle oranssilla (3). Tallenna -painike (4) aktivoituu vasta, kun n&auml;m&auml; pakolliset tiedot on t&auml;ydennetty. Projektiin ei ole pakko t&auml;ydent&auml;&auml; yht&auml;&auml;n tieosaa. Lis&auml;tiedot -kentt&auml;&auml;n k&auml;ytt&auml;j&auml; voi halutessaan tehd&auml; muistiinpanoja tieosoiteprojektista.

![Uusi tieosoiteprojekti](k19.JPG)

_Tieosoiteprojektin perustietojen t&auml;ytt&auml;minen._

Projektin tieosat lis&auml;t&auml;&auml;n t&auml;ydent&auml;m&auml;ll&auml; niiden tiedot kenttiin tie, aosa, losa ja painamalla painiketta Varaa (5). __Kaikki kent&auml;t tulee t&auml;yt&auml;&auml; aina, kun haluaa varata tieosan!__ 

Varaa -painikkeen painamisen j&auml;lkeen tieosan tiedot tulevat n&auml;kyviin lomakkeelle.

![Uusi tieosoiteprojekti](k22.JPG)

_Tieosan tiedot lomakkeella Varaa -painikkeen painamisen j&auml;lkeen._

Tieosoiteprojekti tallennetaan painikkeesta Tallenna, jolloin tiedot tallentuvat tietokantaan ja projektiin p&auml;&auml;see palaamaan Tieosoiteprojektit -listan kautta. Peruuta -painikkeesta projekti suljetaan, ja tallentamattomat tiedot menetet&auml;&auml;n. Sovellus varoittaa peruuttamisen yhteydess&auml; t&auml;st&auml;.

Tallentamisen yhteydess&auml; sovellus zoomaa varatun tieosan alkuun.

![Uusi tieosoiteprojekti](k20.JPG)

_Tallentamisen yhteydess&auml; sovellus zoomaa varatun tieosan alkuun. Tie 71 osa 1 alkaa kohdistimen n&auml;ytt&auml;m&auml;st&auml; kohdasta, ja jatkuu oikealle (oranssi kantatie)._

Varauksen yhteydess&auml; j&auml;rjestelm&auml; tekee varattaville tieosille tarkistukset:

- Onko varattava tieosa olemassa projektin voimaantulop&auml;iv&auml;n&auml;
- Onko varattava tieosa vapaana, eik&auml; varattuna mihink&auml;&auml;n toiseen projektiin

Virheellisist&auml; varausyrityksist&auml; j&auml;rjestelm&auml; antaa asianmukaisen virheilmoituksen. Alla olevassa kuvissa on erilaisista virheilmoituksista, joiden mukaan k&auml;ytt&auml;j&auml;n tulee korjata varausta. __K&auml;ytt&auml;j&auml;n tulee huomioida, ett&auml; varauksen yhteydess&auml; kaikki kent&auml;t (TIE, AOSA, LOSA) tulee t&auml;ytt&auml;&auml;, tai k&auml;ytt&auml;j&auml; saa virheilmoituksen!__

![Uusi tieosoiteprojekti](k21.JPG)

_Yritetty luoda samalla tieosavarauksella tie 71, osa 1 projekti uudelleen. 
Tieosa on kuitenkin jo varattuna aiemmin luotuun projektiin nimelt&auml; "Tieosoitemuutos tie 71 osa 1"._

![Uusi tieosoiteprojekti](k23.JPG)

_Tiet&auml; ei ole olemassa._

![Uusi tieosoiteprojekti](k24.JPG)

_Tieosaa ei ole olemassa._

![Uusi tieosoiteprojekti](k25.JPG)

_Tieosaa ei ole olemassa._

![Uusi tieosoiteprojekti](k40.JPG)

_Ilmoitus, jos varattava tieosa ei ole voimassa p&auml;iv&auml;n&auml;, jolloin projekti tulee voimaan._

9.1 Olemassa olevan tieosoiteprojektin avaaminen Tieosoiteprojektit -listalta
--------------------------

Tallennetun tieosoiteprojektin saa auki Tieosoiteprojektit -listalta painamalla Avaa -painiketta. Avaamisen yhteydess&auml; sovellus zoomaa varatun tieosan alkuun, jotta k&auml;ytt&auml;j&auml; p&auml;&auml;see projektin alueelle.

Tieosoiteprojektit -listalla n&auml;kyv&auml;t kaikkien k&auml;ytt&auml;jien projektit. Projektit ovat projektin nimen mukaisessa aakkosj&auml;rjestyksess&auml;.

![Uusi tieosoiteprojekti](k26.JPG)

_Tieosoiteprojektit -listaus._

10. Muutoksien tekeminen tieosoiteprojektissa
--------------------------

(T&auml;ll&auml; hetkell&auml; toteutettuna vain tieosan lakkauttaminen)

Tieosoiteprojektissa p&auml;&auml;see tekem&auml;&auml;n muutoksia klikkaamalla Seuraava-painiketta tieosoitemuutosprojektin perustietojen lomakkeella. T&auml;m&auml;n j&auml;lkeen sovellus muuttaa varatut tieosat muokattaviksi kohteiksi, jotka n&auml;kyv&auml;t kartalla keltaisella v&auml;rill&auml;.

Projektitilassa vain keltaisella n&auml;kyvi&auml;, projektiin varattuja tieosia voi valita klikkaamalla. Kaikkien tieosien tietoja p&auml;&auml;see kuitenkin n&auml;kem&auml;&auml;n viem&auml;ll&auml; hiiren kartalla tieosoitesegmentin p&auml;&auml;lle, jolloin segmentin infolaatikko tulee n&auml;kyviin.

![Aihio](k36.JPG)

_Projektissa muokattavissa olevat varatut tieosat n&auml;kyv&auml;t kartalla keltaisella v&auml;rill&auml;. Projektin nimi on "Tie 14967 osa 1 lakkautus", joka n&auml;kyy oikeassa yl&auml;kulmassa._

Kun keltaista, muokattavaa kohdetta klikkaa kartala, muuttuu valittu osuus vihre&auml;ksi ja tulee oikeaan laitaan alasvetovalikko, josta voi valita kohteelle teht&auml;v&auml;n toimenpiteen (esim. lakkautus). Kerran klikkaamalla valitaan kartalta homogeeninen jakso, tuplaklikkaus valitsee yhden tieosoitesegmentin verran (tielinkin mittainen osuus).

![Valittu kohde](k37.JPG)

_Kun keltaista, muokattavissa olevaa kohdetta klikataan, tulee oikeaan laitaan n&auml;kyviin tieosoitemuutosprojektin mahdolliset toimenpiteet._

Muutokset tallennetaan oikean alakulman Tallenna-painikkeesta. Ennen tallennusta, voi muutokset perua Peruuta-painikkeesta.

10.1 Muutosilmoituksen l&auml;hett&auml;minen Tierekisteriin
--------------------------

Muutosilmoitus vied&auml;&auml;n Tierekisteriin klikkaamalla oikean alakulman vihre&auml;&auml; Tee tieosoitemuutosilmoitus -painiketta. Painikkeen painamisen j&auml;lkeen sovellus ilmoittaa muutosilmoituksen tekemisest&auml; "Muutosilmoitus l&auml;hetetty Tierekisteriin." -viestill&auml;.

![Muutosilmoituksen painike](k38.JPG)

_Muutosilmoituspainike oikeassa alakulmassa._

Kun muutosilmoitus on l&auml;hetetty, muuttuu projektilistauksessa ko. projektin Tila-tieto statukselle "L&auml;hetetty tierekisteriin" (1). Viite-sovellus tarkistaa 10 minuutin v&auml;lein Tierekisterist&auml;, onko muutos viety Tierekisteriss&auml; loppuun asti. Kun t&auml;m&auml; on tehty, muuttuu Tila-tieto statukselle "Viety tierekisteriin" (2). T&auml;ll&ouml;in tieosoiteprojekti on viety onnistuneesti Tierekisteriin, ja se on valmis.

![Tila-statuksia](k39.JPG)

_Tila-statuksia tieosoiteprojektit -listassa._

