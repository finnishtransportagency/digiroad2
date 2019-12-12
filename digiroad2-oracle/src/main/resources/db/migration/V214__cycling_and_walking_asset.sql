
-- ASSET TYPE VALUE
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
    VALUES ( 440, 'Käpy tietolaji', 'linear', 'db_migration_v214');


-- LOCALIZED_STRING VALUE
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY)
	VALUES( primary_key_seq.nextval, 'Käpy tietolaji', 'db_migration_v214' );


-- PROPERTY VALUE
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED,CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 440, 'single_choice',1,'db_migration_v214', 'cyclingAndWalking_type', (SELECT ID FROM  LOCALIZED_STRING WHERE VALUE_FI = 'Käpy tietolaji'));


-- ENUMERATED VALUES
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v214',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));