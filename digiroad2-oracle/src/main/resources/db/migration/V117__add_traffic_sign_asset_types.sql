-- Add New Asset Type Traffic sign
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
VALUES (300, 'Liikennemerkki', 'point', 'db_migration_v117');

-- Add property Tyyppi
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Tyyppi','db_migration_v117', sysdate);

INSERT INTO PROPERTY (ID,ASSET_TYPE_ID,PROPERTY_TYPE,REQUIRED, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'),'single_choice',0, 'db_migration_v117', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Tyyppi' AND CREATED_BY='db_migration_v117'),'trafficSigns_type');

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Nopeusrajoitus', ' ', 'db_migration_v117', (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Nopeusrajoitus Päättyy', ' ', 'db_migration_v117', (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'Nopeusrajoitusalue', ' ', 'db_migration_v117', (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, 'Nopeusrajoitusalue päättyy', ' ', 'db_migration_v117', (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Taajama', ' ', 'db_migration_v117', (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 6, 'Taajama Päättyy', ' ', 'db_migration_v117', (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 7, 'Suojatie', ' ', 'db_migration_v117', (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 8, 'Suurin Sallittu Pituus', ' ', 'db_migration_v117', (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 9, 'Varoitus', ' ', 'db_migration_v117', (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 10, 'Vasemmalle Kääntyminen Kielletty', ' ', 'db_migration_v117', (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 11, 'Oikealle Kääntyminen Kielletty', ' ', 'db_migration_v117', (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 12, 'U-Käännös Kielletty', ' ', 'db_migration_v117', (select id from property where public_ID = 'trafficSigns_type'));

--Add Property Arvo
INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Arvo','db_migration_v117', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'text', 0, 'db_migration_v117', 'trafficSigns_value', (select id from LOCALIZED_STRING where VALUE_FI = 'Arvo'));

--Add Property Lisätieto
INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Lisätieto','db_migration_v117', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Liikennemerkki'), 'text', 0, 'db_migration_v117', 'trafficSigns_info', (select id from LOCALIZED_STRING where VALUE_FI = 'Lisätieto'));