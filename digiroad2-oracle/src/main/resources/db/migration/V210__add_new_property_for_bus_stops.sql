INSERT INTO LOCALIZED_STRING(ID, VALUE_FI, CREATED_DATE, CREATED_BY)
VALUES (primary_key_seq.nextval, 'Pysäkin palvelutaso', CURRENT_TIMESTAMP , 'db_migration_v210');

INSERT INTO PROPERTY(ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_DATE, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID, DEFAULT_VALUE)
VALUES (primary_key_seq.nextval, (SELECT ID FROM ASSET_TYPE WHERE NAME = 'Bussipysäkit'), 'single_choice', '0', CURRENT_TIMESTAMP , 'db_migration_v210', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Pysäkin palvelutaso'), 'pysakin_palvelutaso', '99');

--Values of dropdown
INSERT INTO ENUMERATED_VALUE(ID, PROPERTY_ID, VALUE, NAME_FI, CREATED_DATE, CREATED_BY)
VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'pysakin_palvelutaso'), 1, 'Terminaali',CURRENT_TIMESTAMP , 'db_migration_v210');

INSERT INTO ENUMERATED_VALUE(ID, PROPERTY_ID, VALUE, NAME_FI, CREATED_DATE, CREATED_BY)
VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'pysakin_palvelutaso'), 2, 'Keskeinen solmupysäkki',CURRENT_TIMESTAMP , 'db_migration_v210');

INSERT INTO ENUMERATED_VALUE(ID, PROPERTY_ID, VALUE, NAME_FI, CREATED_DATE, CREATED_BY)
VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'pysakin_palvelutaso'), 3, 'Vilkas pysäkki',CURRENT_TIMESTAMP , 'db_migration_v210');

INSERT INTO ENUMERATED_VALUE(ID, PROPERTY_ID, VALUE, NAME_FI, CREATED_DATE, CREATED_BY)
VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'pysakin_palvelutaso'), 4, 'Peruspysäkki',CURRENT_TIMESTAMP , 'db_migration_v210');

INSERT INTO ENUMERATED_VALUE(ID, PROPERTY_ID, VALUE, NAME_FI, CREATED_DATE, CREATED_BY)
VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'pysakin_palvelutaso'), 5, 'Vähän käytetty pysäkki',CURRENT_TIMESTAMP , 'db_migration_v210');

INSERT INTO ENUMERATED_VALUE(ID, PROPERTY_ID, VALUE, NAME_FI, CREATED_DATE, CREATED_BY)
VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'pysakin_palvelutaso'), 6, 'Jättöpysäkki',CURRENT_TIMESTAMP , 'db_migration_v210');

INSERT INTO ENUMERATED_VALUE(ID, PROPERTY_ID, VALUE, NAME_FI, CREATED_DATE, CREATED_BY)
VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'pysakin_palvelutaso'), 7, 'Virtuaalipysäkki',CURRENT_TIMESTAMP , 'db_migration_v210');

INSERT INTO ENUMERATED_VALUE(ID, PROPERTY_ID, VALUE, NAME_FI, CREATED_DATE, CREATED_BY)
VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'pysakin_palvelutaso'), 8, 'Pysäkit, jotka eivät ole linja-autoliikenteen käytössä',CURRENT_TIMESTAMP , 'db_migration_v210');

INSERT INTO ENUMERATED_VALUE(ID, PROPERTY_ID, VALUE, NAME_FI, CREATED_DATE, CREATED_BY)
VALUES (primary_key_seq.nextval, (SELECT ID FROM PROPERTY WHERE PUBLIC_ID = 'pysakin_palvelutaso'), 99, 'Ei tietoa',CURRENT_TIMESTAMP , 'db_migration_v210');