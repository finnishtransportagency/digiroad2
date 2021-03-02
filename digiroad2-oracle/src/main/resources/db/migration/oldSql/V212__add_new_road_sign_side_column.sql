INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Liikenteenvastainen', 'db_migration_v212', sysdate);

-- New Properties for the new asset
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 300, 'single_choice', 0, 'db_migration_v212', 'opposite_side_sign', (select max(id) from LOCALIZED_STRING where VALUE_FI = 'Liikenteenvastainen'));

-- Create default values for the field
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 1, 'Kyllä', ' ', 'db_migration_v212', (select id from property where public_ID = 'opposite_side_sign'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
  VALUES (primary_key_seq.nextval, 0, 'Ei', ' ', 'db_migration_v212', (select id from property where public_ID = 'opposite_side_sign'));

