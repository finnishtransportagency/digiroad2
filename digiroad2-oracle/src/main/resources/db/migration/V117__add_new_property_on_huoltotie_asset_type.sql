--Add property Tarkistettu
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Tarkistettu','db_migration_v117', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Huoltotie'), 'checkbox', 0, 'db_migration_v117', 'huoltotie_tarkistettu', (select id from LOCALIZED_STRING where VALUE_FI = 'Tarkistettu'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Tarkistettu', ' ', 'db_migration_v117', (select id from property where public_ID = 'huoltotie_tarkistettu'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Ei tarkistettu', ' ', 'db_migration_v117', (select id from property where public_ID = 'huoltotie_tarkistettu'));


--Update property tiehoitokunta
UPDATE PROPERTY
  SET REQUIRED = 0
WHERE  ASSET_TYPE_ID = (select id from asset_type where name = 'Huoltotie')
  AND PROPERTY_TYPE = 'text'
  AND REQUIRED = 1
  AND PUBLIC_ID = 'huoltotie_tiehoitokunta';
