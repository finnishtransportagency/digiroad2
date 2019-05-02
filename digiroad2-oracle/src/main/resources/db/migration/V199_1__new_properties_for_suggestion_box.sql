INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Vihjetieto','db_migration_v199', sysdate);

-- PROPERTIES FOR SPECIAL ASSETS
--Add property Ajokielto Vihjetieto
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Ajokielto'), 'checkbox', 0, 'db_migration_v199', 'prohibition_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'prohibition_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'prohibition_suggest_box'));

--Add property VAK-rajoitus Vihjetieto
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'VAK-rajoitus'), 'checkbox', 0, 'db_migration_v118', 'hazmat_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'hazmat_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'hazmat_suggest_box'));

--Add property Nopeusrajoitukset Vihjetieto
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Nopeusrajoitukset'), 'checkbox', 0, 'db_migration_v118', 'speedLimit_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'speedLimit_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'speedLimit_suggest_box'));

--Add property Huoltotie Vihjetieto
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Huoltotie'), 'checkbox', 0, 'db_migration_v118', 'huoltotie_vihjetieto', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'huoltotie_vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'huoltotie_vihjetieto'));

-- PROPERTIES FOR POINT ASSETS
--Add property Esterakennelma Tarkistettu
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'esterakennelma'), 'checkbox', 0, 'db_migration_v199', 'obstacle_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'obstacle_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'obstacle_suggest_box'));

--Add property Rautatien tasoristeys Tarkistettu
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Rautatien tasoristeys'), 'checkbox', 0, 'db_migration_v199', 'railway_crossing_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'railway_crossing_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'railway_crossing_suggest_box'));

--Add property Opastustaulu Tarkistettu
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Opastustaulu ja sen informaatio'), 'checkbox', 0, 'db_migration_v199', 'directional_traffic_signs_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'directional_traffic_signs_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'directional_traffic_signs_suggest_box'));

--Add property Suojatie Tarkistettu
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Suojatie'), 'checkbox', 0, 'db_migration_v199', 'pedestrian_crossings_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'pedestrian_crossings_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'pedestrian_crossings_suggest_box'));

--Add property Liikennevalo Tarkistettu
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennevalo'), 'checkbox', 0, 'db_migration_v199', 'traffic_lights_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'traffic_lights_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'traffic_lights_suggest_box'));

--Add property Liikennemerkit Tarkistettu
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'checkbox', 0, 'db_migration_v199', 'traffic_signs_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'traffic_signs_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'traffic_signs_suggest_box'));

--Add property Palvelupiste Tarkistettu
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Palvelupiste'), 'checkbox', 0, 'db_migration_v199', 'service_points_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'service_points_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'service_points_suggest_box'));

--Add property Bussipysäkit Tarkistettu
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), 'checkbox', 0, 'db_migration_v199', 'vihjetieto', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'vihjetieto'));

--Add property Palvelupiste Tarkistettu
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Päällystetty tie'), 'checkbox', 0, 'db_migration_v199', 'paved_road_suggest_box', (select id from LOCALIZED_STRING where VALUE_FI = 'Vihjetieto'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Tarkistettu', ' ', 'db_migration_v199', (select id from property where public_ID = 'paved_road_suggest_box'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Vihjetieto', ' ', 'db_migration_v199', (select id from property where public_ID = 'paved_road_suggest_box'));

-- Change manoeuvre table
ALTER TABLE MANOEUVRE ADD (
  SUGGESTED CHAR(1 BYTE)  DEFAULT '0' CHECK (SUGGESTED in ('0','1'))
);
