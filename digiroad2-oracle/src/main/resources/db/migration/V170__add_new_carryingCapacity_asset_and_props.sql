--Create New Asset
--Carrying Capacity Asset
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
    VALUES (400, 'Kantavuus', 'linear', 'db_migration_v170');


--Create New LOCALIZED_STRING for New Assets
INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval, 'Kevatkantavuus', 'db_migration_v170', sysdate);

INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval, 'Routivuuskerroin', 'db_migration_v170', sysdate);

INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval, 'Mittauspaiva', 'db_migration_v170', sysdate);



--Create New Properties for New Asset
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, 400, 'text', 0, 'db_migration_v170', 'kevatkantavuus', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Kevatkantavuus'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, 400, 'single_choice', 0, 'db_migration_v170', 'routivuuskerroin', (select id from LOCALIZED_STRING where VALUE_FI = 'Routivuuskerroin'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, 400, 'date', 0, 'db_migration_v170', 'mittauspaiva', (select id from LOCALIZED_STRING where VALUE_FI = 'Mittauspaiva'));



--Create default Values for the field Frost Heaving Factor(Routivuuskerroin)
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 40, 'Erittäin routiva', ' ', 'db_migration_v170', (select id from property where public_ID = 'routivuuskerroin'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 50, 'Väliarvo 50...60', ' ', 'db_migration_v170', (select id from property where public_ID = 'routivuuskerroin'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 60, 'Routiva', ' ', 'db_migration_v170', (select id from property where public_ID = 'routivuuskerroin'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 70, 'Väliarvo 60...80', ' ', 'db_migration_v170', (select id from property where public_ID = 'routivuuskerroin'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 80, 'Routimaton', ' ', 'db_migration_v170', (select id from property where public_ID = 'routivuuskerroin'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 999, 'Ei tietoa', ' ', 'db_migration_v170', (select id from property where public_ID = 'routivuuskerroin'));