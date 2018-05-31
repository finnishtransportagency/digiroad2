-- Validity period property
INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'voimassaoloaika','db_migration_v169', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Joukkoliikennekaistat'), 'time_period', 0, 'db_migration_v169', 'public_validity_period', (select id from LOCALIZED_STRING where VALUE_FI = 'voimassaoloaika'));
