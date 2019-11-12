
-- LOCALIZED_STRING Values
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Palvelun Nimi', 'db_migration_v210', sysdate);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Palvelun Lisätieto', 'db_migration_v210', sysdate);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Tarkenne', 'db_migration_v210', sysdate);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Palvelu', 'db_migration_v210', sysdate);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Viranomaisdataa', 'db_migration_v210', sysdate);

-- PROPERTY Values
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM ASSET_TYPE WHERE NAME = 'Bussipysäkit'), 'text', 0, 'db_migration_v210', (SELECT ID FROM LOCALIZED_STRING where VALUE_FI = 'Palvelun Nimi') ,'palvelun_nimi');

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM ASSET_TYPE WHERE NAME = 'Bussipysäkit'), 'long_text', 0, 'db_migration_v210', (SELECT ID FROM LOCALIZED_STRING where VALUE_FI = 'Palvelun Lisätieto') ,'palvelun_lisätieto');

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM ASSET_TYPE WHERE NAME = 'Bussipysäkit'), 'single_choice', 0, 'db_migration_v210', (SELECT ID FROM LOCALIZED_STRING where VALUE_FI = 'Tarkenne') ,'tarkenne');

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM ASSET_TYPE WHERE NAME = 'Bussipysäkit'), 'single_choice', 0, 'db_migration_v210', (SELECT ID FROM LOCALIZED_STRING where VALUE_FI = 'Palvelu') ,'palvelu');

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM ASSET_TYPE WHERE NAME = 'Bussipysäkit'), 'text', 0, 'db_migration_v210', (SELECT ID FROM LOCALIZED_STRING where VALUE_FI = 'Viranomaisdataa') ,'viranomaisdataa');


-- Single Choice values for Palvelu
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID, VALUE, NAME_FI, CREATED_DATE, CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'palvelu'), 11, 'Rautatieasema', SYSDATE, 'db_migration_v210' );

INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID, VALUE, NAME_FI, CREATED_DATE, CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'palvelu'), 8, 'Lentokenttä', SYSDATE, 'db_migration_v210' );

INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID, VALUE, NAME_FI, CREATED_DATE, CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'palvelu'), 9, 'Laivaterminaali', SYSDATE, 'db_migration_v210' );


-- Single Choice values for Tarkenne
INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID, VALUE, NAME_FI, CREATED_DATE, CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'tarkenne'), 5, 'Merkittävä rautatieasema', SYSDATE, 'db_migration_v210' );

INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID, VALUE, NAME_FI, CREATED_DATE, CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'tarkenne'), 6, 'Vähäisempi rautatieasema', SYSDATE, 'db_migration_v210' );

INSERT INTO ENUMERATED_VALUE (ID, PROPERTY_ID, VALUE, NAME_FI, CREATED_DATE, CREATED_BY)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'tarkenne'), 7, 'Maanalainen/metroasema', SYSDATE, 'db_migration_v210' );
