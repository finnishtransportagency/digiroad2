
-- ASSET TYPE VALUE
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
    VALUES ( 440, 'Käpy tietolaji', 'linear', 'db_migration_v215');


-- LOCALIZED_STRING VALUE
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES( primary_key_seq.nextval, 'Käpy tietolaji', 'db_migration_v215', SYSDATE );


-- PROPERTY VALUE
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED,CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 440, 'single_choice',1,'db_migration_v215', 'cyclingAndWalking_type', (SELECT ID FROM  LOCALIZED_STRING WHERE VALUE_FI = 'Käpy tietolaji'));


-- ENUMERATED VALUES
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,1,'Pyöräily ja kävely kielletty','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,2,'Pyöräily kielletty','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,3,'Jalankulun ja pyöräilyn väylä ','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,4,'Maantie tai yksityistie','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,5,'Katu','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,6,'Pyöräkatu','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,7,'Kylätie','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,8,'Kävelykatu','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,9,'Pihakatu','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,10,'Pyöräkaista','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,11,'Pyörätie','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,12,'Kaksisuuntainen pyörätie','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,13,'Yhdistetty pyörätie ja jalkakäytävä, yksisuuntainen pyörille','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,14,'Yhdistetty pyörätie ja jalkakäytävä, kaksisuuntainen pyörille','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,15,'Jalkakäytävä','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,16,'Puistokäytävä','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,17,'Pururata','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,18,'Ajopolku','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,19,'Polku','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval,20,'Lossi tai lautta ','','db_migration_v215',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'));
