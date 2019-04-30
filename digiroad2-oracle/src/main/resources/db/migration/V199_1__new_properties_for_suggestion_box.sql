-- PROPERTIES FOR SPECIAL ASSETS
--Add property Ajokielto Vihjetieto
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Ajokielto Vihjetieto','db_migration_v199', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Ajokielto'), 'checkbox', 0, 'db_migration_v199', 'prohibition_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Ajokielto Vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'prohibition_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'prohibition_suggest_box'));

--Add property VAK-rajoitus Vihjetieto
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'VAK-rajoitus Vihjetieto','db_migration_v199', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'VAK-rajoitus'), 'checkbox', 0, 'db_migration_v118', 'hazmat_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'VAK-rajoitus Vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'hazmat_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'hazmat_suggest_box'));

--Add property Nopeusrajoitukset Vihjetieto
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Nopeusrajoitukset Vihjetieto','db_migration_v199', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Nopeusrajoitukset'), 'checkbox', 0, 'db_migration_v118', 'speedLimit_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Nopeusrajoitukset Vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'speedLimit_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'speedLimit_suggest_box'));

--Add property Kääntymisrajoitus Vihjetieto
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Kääntymisrajoitus Vihjetieto','db_migration_v199', sysdate);


--Add property Huoltotie Vihjetieto
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Huoltotie Vihjetieto','db_migration_v199', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Huoltotie'), 'checkbox', 0, 'db_migration_v118', 'huoltotie_vihjetieto', (select id from LOCALIZED_STRING where VALUE_FI = 'Huoltotie Vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'huoltotie_vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'huoltotie_vihjetieto'));

-- PROPERTIES FOR POINT ASSETS
--Add property Esterakennelma Tarkistettu
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Esterakennelma tarkistettu','db_migration_v199', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'esterakennelma'), 'checkbox', 0, 'db_migration_v199', 'obstacle_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Esterakennelma tarkistettu'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'obstacle_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'obstacle_suggest_box'));

--Add property Rautatien tasoristeys Tarkistettu
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Rautatien tasoristeys tarkistettu','db_migration_v199', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Rautatien tasoristeys'), 'checkbox', 0, 'db_migration_v199', 'railway_crossing_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Rautatien tasoristeys tarkistettu'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'railway_crossing_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'railway_crossing_suggest_box'));

--Add property Opastustaulu Tarkistettu
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Opastustaulu tarkistettu','db_migration_v199', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Opastustaulu ja sen informaatio'), 'checkbox', 0, 'db_migration_v199', 'directional_traffic_signs_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Opastustaulu tarkistettu'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'directional_traffic_signs_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'directional_traffic_signs_suggest_box'));

--Add property Suojatie Tarkistettu
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Suojatie tarkistettu','db_migration_v199', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Suojatie'), 'checkbox', 0, 'db_migration_v199', 'pedestrian_crossings_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Suojatie tarkistettu'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'pedestrian_crossings_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'pedestrian_crossings_suggest_box'));

--Add property Liikennevalo Tarkistettu
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Liikennevalo tarkistettu','db_migration_v199', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennevalo'), 'checkbox', 0, 'db_migration_v199', 'traffic_lights_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Liikennevalo tarkistettu'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'traffic_lights_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'traffic_lights_suggest_box'));

--Add property Liikennemerkit Tarkistettu
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Liikennemerkki tarkistettu','db_migration_v199', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'checkbox', 0, 'db_migration_v199', 'traffic_signs_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Liikennemerkki tarkistettu'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'traffic_signs_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'traffic_signs_suggest_box'));

--Add property Palvelupiste Tarkistettu
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Palvelupiste tarkistettu','db_migration_v199', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Palvelupiste'), 'checkbox', 0, 'db_migration_v199', 'service_points_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Palvelupiste tarkistettu'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'service_points_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'service_points_suggest_box'));

-- Change manoeuvre table
ALTER TABLE MANOEUVRE ADD (
  SUGGESTED CHAR(1 BYTE)  DEFAULT '0' CHECK (SUGGESTED in ('0','1'))
);
