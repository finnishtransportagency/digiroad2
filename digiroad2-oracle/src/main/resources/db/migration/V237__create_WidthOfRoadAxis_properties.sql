-- ASSET TYPE VALUE
INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
    VALUES ( 470, 'Poikittaissuuntaiset tiemerkinnät', 'point', 'db_migration_v237');




-- LOCALIZED_STRING VALUE
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES( primary_key_seq.nextval, 'Tiennimi', 'db_migration_v237', SYSDATE );

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES( primary_key_seq.nextval, 'Asetuksen Numero', 'db_migration_v237', SYSDATE );

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES( primary_key_seq.nextval, 'Kaistanro', 'db_migration_v237', SYSDATE );

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES( primary_key_seq.nextval, 'Kaistatyyp', 'db_migration_v237', SYSDATE );

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES( primary_key_seq.nextval, 'Merkinnän Materiaali', 'db_migration_v237', SYSDATE );

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES( primary_key_seq.nextval, 'Merkinnän Pituus', 'db_migration_v237', SYSDATE );

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES( primary_key_seq.nextval, 'Merkinnän Leveys', 'db_migration_v237', SYSDATE );

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES( primary_key_seq.nextval, 'Profiilimerkintä', 'db_migration_v237', SYSDATE );

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_BY, CREATED_DATE)
	VALUES( primary_key_seq.nextval, 'Jyrsitty', 'db_migration_v237', SYSDATE );





-- PROPERTY VALUE
INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED,CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 470, 'text', 0,'db_migration_v237', 'widthOfRoadAxisMarking_road_name', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Tiennimi'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED,CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 470, 'single_choice', 1,'db_migration_v237', 'widthOfRoadAxisMarking_regulation_number', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Asetuksen Numero'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED,CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 470, 'number', 0,'db_migration_v237', 'widthOfRoadAxisMarking_lane_number', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Kaistanro'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED,CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 470, 'number', 0,'db_migration_v237', 'widthOfRoadAxisMarking_lane_type', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Kaistatyyp'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED,CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 470, 'single_choice', 0,'db_migration_v237', 'widthOfRoadAxisMarking_condition', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Kunto'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED,CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 470, 'single_choice', 0,'db_migration_v237', 'widthOfRoadAxisMarking_material', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Merkinnän Materiaali'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED,CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 470, 'number', 0,'db_migration_v237', 'widthOfRoadAxisMarking_length', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Merkinnän Pituus'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED,CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 470, 'number', 0,'db_migration_v237', 'widthOfRoadAxisMarking_width', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Merkinnän Leveys'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED,CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 470, 'single_choice', 0,'db_migration_v237', 'widthOfRoadAxisMarking_raised', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Profiilimerkintä'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED,CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 470, 'single_choice', 0,'db_migration_v237', 'widthOfRoadAxisMarking_milled', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Jyrsitty'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED,CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 470, 'text', 0,'db_migration_v237', 'widthOfRoadAxisMarking_additional_info', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Lisätieto'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED,CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 470, 'single_choice', 0,'db_migration_v237', 'widthOfRoadAxisMarking_state', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Tila'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED,CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 470, 'date', 0,'db_migration_v237', 'widthOfRoadAxisMarking_start_date', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Alkupäivämäärä'));

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED,CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
	VALUES (primary_key_seq.nextval, 470, 'date', 0,'db_migration_v237', 'widthOfRoadAxisMarking_end_date', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Loppupäivämäärä'));



-- ENUMERATED VALUES
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 1,'L1 pysäytysviiva','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_regulation_number'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 2,'L2 Väistämisviiva','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_regulation_number'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 3,'L3 Suojatie','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_regulation_number'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 41,'L4.1 Pyörätien jatke','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_regulation_number'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 42,'L4.2 Pyörätien jatke','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_regulation_number'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 43,'L4.3 Pyörätien jatke','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_regulation_number'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 51,'L5.1 Töyssy','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_regulation_number'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 52,'L5.2 Töyssy','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_regulation_number'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 6,'L6 Heräteraidat','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_regulation_number'));


INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 1,'Erittäin huono','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_condition'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 2,'Huono','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_condition'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 3,'Tyydyttävä','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_condition'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 4,'Hyvä','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_condition'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 5,'Erittäin hyvä','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_condition'));


INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 1,'Ei','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_material'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 2,'Kyllä','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_material'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 99,'Ei tiedossa','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_material'));


INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 1,'Ei','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_raised'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 2,'Kyllä','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_raised'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 99,'Ei tiedossa','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_raised'));


INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 1,'Ei','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_milled'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 2,'Pintamerkintä','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_milled'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 3,'Siniaalto','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_milled'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 4,'Sylinterimerkintä','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_milled'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 99,'Ei tiedossa','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_milled'));


INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 1,'Suunnitteilla ','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_state'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 2,'Rakenteilla ','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_state'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 3,'Käytössä pysyvästi (oletus)','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_state'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 4,'Käytössä tilapäisesti','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_state'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 5,'Pois käytössä tilapäisesti','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_state'));
INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
	VALUES (primary_key_seq.nextval, 6,'Poistuva pysyvä laite','','db_migration_v237',(SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'widthOfRoadAxisMarking_state'));