--Add property Vyöhyketieto
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Vyöhyketieto','db_migration_v152', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID, MAX_VALUE_LENGTH)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), 'text', 0, 'db_migration_v152', 'vyöhyketieto', (select id from LOCALIZED_STRING where VALUE_FI = 'Vyöhyketieto'), 30);