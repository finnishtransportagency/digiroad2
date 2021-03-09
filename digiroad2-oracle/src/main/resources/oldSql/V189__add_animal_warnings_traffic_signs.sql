--Create Traffic Signs for Animal Warnings
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval,  125 , 'Hirvieläimiä', ' ' , 'db_migration_v185',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval,  126 , 'Poroja', ' ' , 'db_migration_v185',  (select id from property where public_ID = 'trafficSigns_type'));


--Create New Asset
--Animal Warning Asset
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
    VALUES (410, 'Elainvaroitukset', 'linear', 'db_migration_v185');

--Create New LOCALIZED_STRING for New Assets
INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval, 'Hirvivaro', 'db_migration_v185', sysdate);

--Create New Properties for New Asset
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, 410, 'single_choice', 0, 'db_migration_v185', 'hirvivaro', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Hirvivaro'));

--Create default Values for the field moose Warning(hirvivaro)
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 1, 'Hirvivaroitusalue', ' ', 'db_migration_v185', (select id from property where public_ID = 'hirvivaro'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 2, 'Hirviaita', ' ', 'db_migration_v185', (select id from property where public_ID = 'hirvivaro'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 3, 'Peuravaroitusalue', ' ', 'db_migration_v185', (select id from property where public_ID = 'hirvivaro'));