--Create New Asset
--Roadworks Asset
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
    VALUES (420, 'Tietyot', 'linear', 'db_migration_v197');

--Create New LOCALIZED_STRING for New Asset
INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval, 'Työn tunnus', 'db_migration_v197', sysdate);

INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval, 'Arvioitu kesto', 'db_migration_v197', sysdate);

--Create New Properties for New Asset
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, 420, 'number', 0, 'db_migration_v197', 'tyon_tunnus', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Työn tunnus'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, 420, 'date_period', 0, 'db_migration_v197', 'arvioitu_kesto', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Arvioitu kesto'));



