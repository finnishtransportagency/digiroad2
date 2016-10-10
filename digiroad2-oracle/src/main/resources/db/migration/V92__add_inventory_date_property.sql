INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, VALUE_SV, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Inventointipäivä', NULL, 'db_migration_v92', sysdate);
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 10, 'text', '0', 'db_migration_v92', 'inventointipaiva', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Inventointipäivä' AND CREATED_BY='db_migration_v92'));
