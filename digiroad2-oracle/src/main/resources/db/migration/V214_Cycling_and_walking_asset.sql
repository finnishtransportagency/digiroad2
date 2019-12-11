
-- ASSET TYPE VALUE
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_DATE,CREATED_BY)
    VALUES ( 440, 'Käpy tietolaji', 'linear', SYSDATE, 'db_migration_v214');


-- LOCALIZED_STRING VALUE
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_DATE, CREATED_BY)
	VALUES( primary_key_seq.nextval, 'Käpy tietolaji', SYSDATE, 'db_migration_v214' );


-- PROPERTY VALUE
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED,CREATED_DATE,CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
	VALUES (primary_key_seq.nextval, 440, 'single_choice',1,SYSDATE,'db_migration_v214', (SELECT ID FROM  LOCALIZED_STRING WHERE VALUE_FI = 'Käpy tietolaji'), 'cyclingAndWalking_type');


-- ENUMERATED VALUES
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID,VALUE,NAME_FI,CREATED_DATE,CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'),1,'Pyöräily kielletty',Sysdate,'db_migration_v214');
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID,VALUE,NAME_FI,CREATED_DATE,CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'),2,'Jalankulun ja pyöräilyn väylä',Sysdate,'db_migration_v214');
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID,VALUE,NAME_FI,CREATED_DATE,CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'),3,'Katu',Sysdate,'db_migration_v214');
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID,VALUE,NAME_FI,CREATED_DATE,CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'),4,'Maantie tai yksityistie',Sysdate,'db_migration_v214');
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID,VALUE,NAME_FI,CREATED_DATE,CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'),5,'Pyöräkatu',Sysdate,'db_migration_v214');
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID,VALUE,NAME_FI,CREATED_DATE,CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'),6,'Kylätie',Sysdate,'db_migration_v214');
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID,VALUE,NAME_FI,CREATED_DATE,CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'),7,'Pihakatu',Sysdate,'db_migration_v214');
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID,VALUE,NAME_FI,CREATED_DATE,CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'),8,'Kävelykatu',Sysdate,'db_migration_v214');
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID,VALUE,NAME_FI,CREATED_DATE,CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'),9,'Pyöräkaista',Sysdate,'db_migration_v214');
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID,VALUE,NAME_FI,CREATED_DATE,CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'),10,'Pyörätie',Sysdate,'db_migration_v214');
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID,VALUE,NAME_FI,CREATED_DATE,CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'),11,'Kaksisuuntainen pyörätie',Sysdate,'db_migration_v214');
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID,VALUE,NAME_FI,CREATED_DATE,CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'),12,'Yhdistetty pyörätie ja jalkakäytävä, yksisuuntainen pyörille',Sysdate,'db_migration_v214');
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID,VALUE,NAME_FI,CREATED_DATE,CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'),13,'Yhdistetty pyörätie ja jalkakäytävä, kaksisuuntainen pyörille',Sysdate,'db_migration_v214');
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID,VALUE,NAME_FI,CREATED_DATE,CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'),14,'Puistokäytävä',Sysdate,'db_migration_v214');
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID,VALUE,NAME_FI,CREATED_DATE,CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'),15,'Jalkakäytävä',Sysdate,'db_migration_v214');
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID,VALUE,NAME_FI,CREATED_DATE,CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'),16,'Pururata',Sysdate,'db_migration_v214');
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID,VALUE,NAME_FI,CREATED_DATE,CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'),17,'Ajopolku',Sysdate,'db_migration_v214');
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID,VALUE,NAME_FI,CREATED_DATE,CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'cyclingAndWalking_type'),18,'Polku',Sysdate,'db_migration_v214');