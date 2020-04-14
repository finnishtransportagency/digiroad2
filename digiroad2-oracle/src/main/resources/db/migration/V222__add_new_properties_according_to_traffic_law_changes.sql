--Add property Päämerkin teksti
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Päämerkin teksti','db_migration_v222', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'text', 0, 'db_migration_v222', 'main_sign_text', (select id from LOCALIZED_STRING where VALUE_FI = 'Päämerkin teksti'));

--Add property Sijaintitarkenne
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Sijaintitarkenne','db_migration_v222', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'single_choice', 0, 'db_migration_v222', 'location_specifier', (select id from LOCALIZED_STRING where VALUE_FI = 'Sijaintitarkenne'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Väylän oikea puoli', ' ', 'db_migration_v222', (select id from property where public_ID = 'location_specifier'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Väylän vasen puoli', ' ', 'db_migration_v222', (select id from property where public_ID = 'location_specifier'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Kaistan yläpuolella', ' ', 'db_migration_v222', (select id from property where public_ID = 'location_specifier'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, 'Keskisaareke tai liikenteenjakaja', ' ', 'db_migration_v222', (select id from property where public_ID = 'location_specifier'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Pitkittäin ajosuuntaan nähden', ' ', 'db_migration_v222', (select id from property where public_ID = 'location_specifier'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 6, 'Tie- ja katuverkon puolella, esimerkiksi parkkialueella tai pihaalueella', ' ', 'db_migration_v222', (select id from property where public_ID = 'location_specifier'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 999, 'Ei tietoa', ' ', 'db_migration_v222', (select id from property where public_ID = 'location_specifier'));

--Add property Rakenne
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Rakenne','db_migration_v222', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'single_choice', 0, 'db_migration_v222', 'structure', (select id from LOCALIZED_STRING where VALUE_FI = 'Rakenne'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Tolppa', ' ', 'db_migration_v222', (select id from property where public_ID = 'structure'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Seinä', ' ', 'db_migration_v222', (select id from property where public_ID = 'structure'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Silta', ' ', 'db_migration_v222', (select id from property where public_ID = 'structure'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, 'Portaali', ' ', 'db_migration_v222', (select id from property where public_ID = 'structure'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Puomi tai muu esterakennelma', ' ', 'db_migration_v222', (select id from property where public_ID = 'structure'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 6, 'Muu', ' ', 'db_migration_v222', (select id from property where public_ID = 'structure'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 999, 'Ei tietoa', ' ', 'db_migration_v222', (select id from property where public_ID = 'structure'));

--Add property Kunto
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Kunto','db_migration_v222', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'single_choice', 0, 'db_migration_v222', 'condition', (select id from LOCALIZED_STRING where VALUE_FI = 'Kunto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Erittäin huono', ' ', 'db_migration_v222', (select id from property where public_ID = 'condition'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Huono', ' ', 'db_migration_v222', (select id from property where public_ID = 'condition'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Tyydyttävä', ' ', 'db_migration_v222', (select id from property where public_ID = 'condition'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, 'Hyvä', ' ', 'db_migration_v222', (select id from property where public_ID = 'condition'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Erittäin hyvä', ' ', 'db_migration_v222', (select id from property where public_ID = 'condition'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 999, 'Ei tietoa', ' ', 'db_migration_v222', (select id from property where public_ID = 'condition'));

--Add property Koko
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Koko','db_migration_v222', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'single_choice', 0, 'db_migration_v222', 'size', (select id from LOCALIZED_STRING where VALUE_FI = 'Koko'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Pienikokoinen merkki', ' ', 'db_migration_v222', (select id from property where public_ID = 'size'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Normaalikokoinen merkki', ' ', 'db_migration_v222', (select id from property where public_ID = 'size'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Suurikokoinen merkki', ' ', 'db_migration_v222', (select id from property where public_ID = 'size'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 999, 'Ei tietoa', ' ', 'db_migration_v222', (select id from property where public_ID = 'size'));

--Add property Korkeus
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Korkeus','db_migration_v222', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'number', 0, 'db_migration_v222', 'height', (select id from LOCALIZED_STRING where VALUE_FI = 'Korkeus'));

--Add property Kalvon tyyppi
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Kalvon tyyppi','db_migration_v222', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'single_choice', 0, 'db_migration_v222', 'coating_type', (select id from LOCALIZED_STRING where VALUE_FI = 'Kalvon tyyppi'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'R1-luokan kalvo', ' ', 'db_migration_v222', (select id from property where public_ID = 'coating_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'R2-luokan kalvo', ' ', 'db_migration_v222', (select id from property where public_ID = 'coating_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'R3-luokan kalvo', ' ', 'db_migration_v222', (select id from property where public_ID = 'coating_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 999, 'Ei tietoa', ' ', 'db_migration_v222', (select id from property where public_ID = 'coating_type'));

--Add property Kaista
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Kaista','db_migration_v222', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'number', 0, 'db_migration_v222', 'lane', (select id from LOCALIZED_STRING where VALUE_FI = 'Kaista'));

--Add property Tila
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Tila','db_migration_v222', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'single_choice', 0, 'db_migration_v222', 'life_cycle', (select id from LOCALIZED_STRING where VALUE_FI = 'Tila'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Suunnitteilla', ' ', 'db_migration_v222', (select id from property where public_ID = 'life_cycle'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Rakenteilla', ' ', 'db_migration_v222', (select id from property where public_ID = 'life_cycle'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Toteutuma', ' ', 'db_migration_v222', (select id from property where public_ID = 'life_cycle'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, 'Käytössä tilapäisesti', ' ', 'db_migration_v222', (select id from property where public_ID = 'life_cycle'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Pois käytöstä tilapaisesti', ' ', 'db_migration_v222', (select id from property where public_ID = 'life_cycle'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 99, 'Poistuva pysyvä laite', ' ', 'db_migration_v222', (select id from property where public_ID = 'life_cycle'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 999, 'Ei tietoa', ' ', 'db_migration_v222', (select id from property where public_ID = 'life_cycle'));

--Add property Merkin materiaali
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Merkin materiaali','db_migration_v222', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'single_choice', 0, 'db_migration_v222', 'sign_material', (select id from LOCALIZED_STRING where VALUE_FI = 'Merkin materiaali'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vaneri', ' ', 'db_migration_v222', (select id from property where public_ID = 'sign_material'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Alumiini', ' ', 'db_migration_v222', (select id from property where public_ID = 'sign_material'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Muu', ' ', 'db_migration_v222', (select id from property where public_ID = 'sign_material'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 999, 'Ei tietoa', ' ', 'db_migration_v222', (select id from property where public_ID = 'sign_material'));

--Add property old_traffic_code
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Lisää vanhan lain mukainen koodi','db_migration_v222', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'checkbox', 0, 'db_migration_v222', 'old_traffic_code', (select id from LOCALIZED_STRING where VALUE_FI = 'Lisää vanhan lain mukainen koodi'));

INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Totta','db_migration_v222', sysdate);

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Totta', ' ', 'db_migration_v222', (select id from property where public_ID = 'old_traffic_code'));

INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Väärä','db_migration_v222', sysdate);

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Väärä', ' ', 'db_migration_v222', (select id from property where public_ID = 'old_traffic_code'));

--Modify additional panel table to accommodate the new additional panel properties
ALTER TABLE ADDITIONAL_PANEL ADD ADDITIONAL_SIGN_TEXT VARCHAR2 (128);
ALTER TABLE ADDITIONAL_PANEL ADD ADDITIONAL_SIGN_SIZE NUMBER;
ALTER TABLE ADDITIONAL_PANEL ADD ADDITIONAL_SIGN_COATING_TYPE NUMBER;
ALTER TABLE ADDITIONAL_PANEL ADD ADDITIONAL_SIGN_PANEL_COLOR NUMBER;

--Additional fields for the form
--Add property lane_type
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Kaistan tyyppi','db_migration_v222', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'single_choice', 0, 'db_migration_v222', 'lane_type', (select id from LOCALIZED_STRING where VALUE_FI = 'Kaistan tyyppi'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Pääkaista', ' ', 'db_migration_v222', (select id from property where public_ID = 'lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Ohituskaista', ' ', 'db_migration_v222', (select id from property where public_ID = 'lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Kääntymiskaista oikealle', ' ', 'db_migration_v222', (select id from property where public_ID = 'lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, 'Kääntymiskaista vasemmalle', ' ', 'db_migration_v222', (select id from property where public_ID = 'lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Lisäkaista suoraan ajaville', ' ', 'db_migration_v222', (select id from property where public_ID = 'lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 6, 'Liittymiskaista', ' ', 'db_migration_v222', (select id from property where public_ID = 'lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 7, 'Erkanemiskaista', ' ', 'db_migration_v222', (select id from property where public_ID = 'lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 8, 'Sekoittumiskaista', ' ', 'db_migration_v222', (select id from property where public_ID = 'lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 9, 'Joukkoliikenteen kaista / taksikaista', ' ', 'db_migration_v222', (select id from property where public_ID = 'lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 10, 'Raskaan liikenteen kaista', ' ', 'db_migration_v222', (select id from property where public_ID = 'lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 11, 'Vaihtuvasuuntainen kaista', ' ', 'db_migration_v222', (select id from property where public_ID = 'lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 20, 'Yhdistetty jalankulun ja pyöräilyn kaista', ' ', 'db_migration_v222', (select id from property where public_ID = 'lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 21, 'Jalankulun kaista', ' ', 'db_migration_v222', (select id from property where public_ID = 'lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 22, 'Pyöräilykaista', ' ', 'db_migration_v222', (select id from property where public_ID = 'lane_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 999, 'Ei tiedossa', ' ', 'db_migration_v222', (select id from property where public_ID = 'lane_type'));

--Add property type_of_damage
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Vauriotyyppi','db_migration_v222', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'single_choice', 0, 'db_migration_v222', 'type_of_damage', (select id from LOCALIZED_STRING where VALUE_FI = 'Vauriotyyppi'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Ruostunut', ' ', 'db_migration_v222', (select id from property where public_ID = 'type_of_damage'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Kolhiintunut', ' ', 'db_migration_v222', (select id from property where public_ID = 'type_of_damage'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Maalaus ', ' ', 'db_migration_v222', (select id from property where public_ID = 'type_of_damage'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, 'Muu vauiro', ' ', 'db_migration_v222', (select id from property where public_ID = 'type_of_damage'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 999, 'Ei tiedossa', ' ', 'db_migration_v222', (select id from property where public_ID = 'type_of_damage'));

--Add property urgency_of_repair
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Korjauksen kiireellisyys','db_migration_v222', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'single_choice', 0, 'db_migration_v222', 'urgency_of_repair', (select id from LOCALIZED_STRING where VALUE_FI = 'Korjauksen kiireellisyys'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Erittäin kiireellinen', ' ', 'db_migration_v222', (select id from property where public_ID = 'urgency_of_repair'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Kiireellinen', ' ', 'db_migration_v222', (select id from property where public_ID = 'urgency_of_repair'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Jokseenkin kiireellinen', ' ', 'db_migration_v222', (select id from property where public_ID = 'urgency_of_repair'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, 'Ei kiireellinen', ' ', 'db_migration_v222', (select id from property where public_ID = 'urgency_of_repair'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 999, 'Ei tiedossa', ' ', 'db_migration_v222', (select id from property where public_ID = 'urgency_of_repair'));

--Add property lifespan_left
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Arvioitu käyttöikä','db_migration_v222', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'number', 0, 'db_migration_v222', 'lifespan_left', (select id from LOCALIZED_STRING where VALUE_FI = 'Arvioitu käyttöikä'));

--Add property municipality_id
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Kunnan ID','db_migration_v222', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'text', 0, 'db_migration_v222', 'municipality_id', (select id from LOCALIZED_STRING where VALUE_FI = 'Kunnan ID'));

--Add property relation terrain_coordinates
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'number', 0, 'db_migration_v222', 'terrain_coordinates_x', (select id from LOCALIZED_STRING where VALUE_FI = 'Maastokoordinaatti X'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'number', 0, 'db_migration_v222', 'terrain_coordinates_y', (select id from LOCALIZED_STRING where VALUE_FI = 'Maastokoordinaatti Y'));