-- Add property roska_astia
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, VALUE_SV, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Roska-astia', NULL, 'db_migration_v89', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 10, 'single_choice', 0, 'db_migration_v89', 'roska_astia', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Roska-astia' AND CREATED_BY='db_migration_v89'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Ei', 'db_migration_v89', (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'roska_astia'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Kyllä', 'db_migration_v89', (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'roska_astia'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 99, 'Ei tietoa', 'db_migration_v89', (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'roska_astia'));

-- Add property koroke
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, VALUE_SV, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval, 'Koroke', NULL, 'db_migration_v89', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, 10, 'single_choice', 0, 'db_migration_v89', 'koroke', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Koroke' AND CREATED_BY='db_migration_v89'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Ei', 'db_migration_v89', (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'koroke'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Kyllä', 'db_migration_v89', (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'koroke'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 99, 'Ei tietoa', 'db_migration_v89', (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'koroke'));
