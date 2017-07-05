VVH koodiarvoluettelo
=========================================

Ominaisuustietotaulussa kent&auml;n nimi ja koodiarvot n&auml;kyv&auml;t selke&auml;mp&auml;n&auml; versiona. Field Calculator ja Select by Attributes -ty&ouml;kalut k&auml;ytt&auml;v&auml;t kuitenkin tietokannan kenttien nimi&auml; ja koodiarvoja. 

Alla olevaan taulukkoon on kerrottu kenttien nimet molemmilla tavoilla, sek&auml; kunkin kent&auml;n koodiarvoluettelot, jos kentt&auml; on koodiarvollinen.


|Ominaisuustieto|Tyyppi|Koodiarvot|
|---------------|------|-----------|
|Objectid (OBJECTID)|Kokonaisluku||
|Digiroad-tunniste (DRID)|Kokonaisluku||
|Linkkitunniste (LINKID)|Kokonaisluku||
|Ty&ouml;n tunniste (JOBID)|Kokonaisluku||
|Aineistol&auml;hde (SOURCEINFO)|Koodiarvo|0 Muu<BR>1 Maanmittauslaitos<BR>2 SYKE<BR>3 Merenkulkulaitos<BR>4 Mets&auml;hallitus<BR>5 Puolustusvoimat<BR>6 Kunta<BR>7 Liikennevirasto|
|Hallinnollinen luokka (ADMINCLASS)|Koodiarvo|1 Valtio<BR>2 Kunta<BR>3 Yksityinen|
|Kohderyhm&auml; (MTKGROUP)|Koodiarvo|24 Suunnitelmatiest&ouml;<BR>25 Tiest&ouml; (viiva)<BR>45 Tiest&ouml; (piste)<BR>64 Tiest&ouml; (alue)|
|Valmiusaste (CONSTRUCTIONTYPE)|Koodiarvo|0 K&auml;yt&ouml;ss&auml;<BR>1 Rakenteilla<BR>3 Suunnitteilla|
|Yksisuuntaisuus (DIRECTIONTYPE)|Koodiarvo|0 Kaksisuuntainen<BR>1 Digitointisuunnassa<BR>2 Digitointisuuntaa vastaan|
|Tasosijainti (VERTICALLEVEL)|Koodiarvo|10 M&auml;&auml;rittelem&auml;t&ouml;n<BR>0 Pinnalla<BR>-1 Pinnan alla<BR>1 Pinnan yll&auml; taso 1<BR>2 Pinnan yll&auml; taso 2<BR>3 Pinnan yll&auml; taso 3<BR>4 Pinnan yll&auml; taso 4<BR>5 Pinnan yll&auml; taso 5<BR>-11 Tunnelissa|
|Kuntatunnus (MUNICIPALITYCODE)|Koodiarvo|Koodiarvo kuntanumeron mukaan|
|Hankkeen arvioitu valmistuminen (ESTIMATED_COMPLETION)|P&auml;iv&auml;m&auml;&auml;r&auml; (pp.kk.vvvv)||
|MTK-tunniste (MTKID)|Kokonaisluku||
|Kohdeluokka (MTKCLASS)|Koodiarvo|Suravagessa k&auml;yt&ouml;ss&auml; vain luokka 12314 K&auml;vely- ja/tai py&ouml;r&auml;tie<BR>|
|Tienumero (ROADNUMBER)|Kokonaisluku||
|Tieosanumero (ROADPARTNUMBER)|Kokonaisluku||
|Ajoratakoodi (TRACK_CODE)|Kokonaisluku|K&auml;yt&auml;nn&ouml;ss&auml; arvo 0, 1 tai 2|
|P&auml;&auml;llystetieto (SURFACETYPE)|Koodiarvo|0 Tuntematon<BR>1 Ei p&auml;&auml;llystett&auml;<BR>2 Kestop&auml;&auml;llyste|
|Sijaintitarkkuus (HORIZONTALACCURACY)|Koodiarvo|Ei k&auml;yt&ouml;ss&auml; Suravagessa<BR>500 0,5 m<BR>800 0,8 m<BR>1000 1 m<BR>10000 10 m<BR>100000 100m<BR>12500 12,5 m<BR>15000 15 m<BR>2000 2 m<BR>20000 20 m<BR>25000 25 m<BR>3000 3 m<BR>30000 30 m<BR>4000 4 m<BR>5000 5 m<BR>7500 7,5 m<BR>8000 8 m<BR>80000 80 m<BR>0 Ei m&auml;&auml;ritelty|
|Korkeustarkkuus (VERTICALACCURACY)|Koodiarvo|Ei k&auml;yt&ouml;sss&auml; Suravagessa<BR>500 0,5 m<BR>800 0,8 m<BR>1000 1 m<BR>10000 10 m<BR>100000 100m<BR>12500 12,5 m<BR>15000 15 m<BR>2000 2 m<BR>20000 20 m<BR>25000 25 m<BR>3000 3 m<BR>30000 30 m<BR>4000 4 m<BR>5000 5 m<BR>7500 7,5 m<BR>8000 8 m<BR>80000 80 m<BR>1 Ei m&auml;&auml;ritelty<BR>100001 KM 10 m<BR>201 KM 2 m<BR>250001 KM 25 m|
|Kulkutapa (VECTORTYPE)|Koodiarvo|1 Murto<BR>2 K&auml;yr&auml;|
|Pituus (GEOMETRYLENGTH)|Desimaaliluku||
|Osoitenumero, vasen, alku (FROM_LEFT)|Kokonaisluku||
|Osoitenumero, vasen, loppu (TO_LEFT)|Kokonaisluku||
|Osoitenumero, oikea, alku (FROM_RIGHT)|Kokonaisluku||
|Osoitenumero, oikea, loppu (TO_RIGHT)|Kokonaisluku||
|Voimassaolo, alku (VALID_FROM)|P&auml;iv&auml;m&auml;&auml;r&auml;||
|Voimassaolo, loppu (VALID_TO)|P&auml;iv&auml;m&auml;&auml;r&auml;||
|Perustusp&auml;iv&auml; (CREATED_DATE)|P&auml;iv&auml;m&auml;&auml;r&auml;||
|Perustaja (CREATED_BY)|Tekstikentt&auml;||
|Validointistatus (VALIDATIONSTATUS)|Koodiarvo|0 Ei tarkastettu<BR>1 Virheellinen<BR>2 Hyv&auml;ksytty<BR>3 K&auml;ytt&auml;j&auml;n hyv&auml;ksym&auml;|
|Kohteen olotila (OBJECTSATUS)|Koodiarvo|1 Alkuper&auml;inen<BR>2 Muokattu ominaisuuksia<BR>3 Muokattu<BR>4 Uusi<BR>8 Merkattu poistettavaksi<BR>9 Poistettu|
|Tien nimi (suomi) (ROADNAME_FI)|Tekstikentt&auml;||
|Tien nimi (ruotsi) (ROADNAME_SE)|Tekstikentt&auml;||
|Tien nimi (saame) (ROADNAME_SM)|Tekstikentt&auml;||
|MTKHEREFLIP|Koodiarvo|0 Ei k&auml;&auml;nnetty<BR>1 K&auml;&auml;nnetty|

![Koodiarvot](k55.png)

_Hallinnollinen luokka ominaisuustietotaulussa, Field Calculatorissa ja Select by Attributes -ty&ouml;kalussa._
