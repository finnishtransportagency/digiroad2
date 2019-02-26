--Create New Asset
--Roadworks Asset
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
    VALUES (420, 'Tietyot', 'linear', 'db_migration_v195');

--Create New LOCALIZED_STRING for New Asset
INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval, 'Työn tunnus', 'db_migration_v195', sysdate);

INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval, 'Arvioitu aloituspäivä', 'db_migration_v195', sysdate);

INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
    VALUES (primary_key_seq.nextval, 'Arvioitu valmistumispäivä', 'db_migration_v195', sysdate);

--Create New Properties for New Asset
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, 420, 'integer', 0, 'db_migration_v195', 'tyon_tunnus', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Työn tunnus'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, 420, 'date_period', 0, 'db_migration_v195', 'arvioitu_aloituspaiva', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Arvioitu aloituspäivä'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
    VALUES (primary_key_seq.nextval, 420, 'date_period', 0, 'db_migration_v195', 'arvioitu_valmistumispaiva', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Arvioitu valmistumispäivä'));


