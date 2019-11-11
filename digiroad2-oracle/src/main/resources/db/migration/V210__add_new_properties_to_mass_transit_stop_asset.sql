INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Palvelun Lisätieto', 'db_migration_v210', sysdate);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Tarkenne', 'db_migration_v210', sysdate);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Palvelu', 'db_migration_v210', sysdate);

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES (primary_key_seq.nextval, 'Viranomaisdataa', 'db_migration_v210', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM ASSET_TYPE WHERE NAME = 'Bussipysäkit'), 'text', 0, 'db_migration_v210', (SELECT ID FROM LOCALIZED_STRING where VALUE_FI = 'Palvelun Lisätieto') ,'palvelun_lisätieto');

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM ASSET_TYPE WHERE NAME = 'Bussipysäkit'), 'number', 0, 'db_migration_v210', (SELECT ID FROM LOCALIZED_STRING where VALUE_FI = 'Tarkenne') ,'tarkenne');

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM ASSET_TYPE WHERE NAME = 'Bussipysäkit'), 'number', 0, 'db_migration_v210', (SELECT ID FROM LOCALIZED_STRING where VALUE_FI = 'Palvelu') ,'palvelu');

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
	VALUES (primary_key_seq.nextval, (SELECT ID FROM ASSET_TYPE WHERE NAME = 'Bussipysäkit'), 'number', 0, 'db_migration_v210', (SELECT ID FROM LOCALIZED_STRING where VALUE_FI = 'Viranomaisdataa') ,'viranomaisdataa');
