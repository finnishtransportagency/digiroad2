--Add property Tarkistettu
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Tarkistettu','db_migration_v116', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Huoltotie'), 'checkbox', 0, 'db_migration_v116', 'huoltotie_tarkistettu', (select id from LOCALIZED_STRING where VALUE_FI = 'Tarkistettu'));

--Update property
UPDATE PROPERTY
  SET REQUIRED = 0
WHERE  ASSET_TYPE_ID = (select id from asset_type where name = 'Huoltotie')
  AND PROPERTY_TYPE = 'text'
  AND REQUIRED = 1
  AND PUBLIC_ID = 'huoltotie_tiehoitokunta';
