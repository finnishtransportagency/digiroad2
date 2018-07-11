-- Paallysteluokka
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Paallysteluokka','db_migration_v175', sysdate);

INSERT INTO PROPERTY (ID,ASSET_TYPE_ID,PROPERTY_TYPE, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Päällystetty tie'),'single_choice', 'db_migration_v175', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Paallysteluokka' AND CREATED_BY='db_migration_v175'),'paallysteluokka_paallystetty_tie');

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Betoni', ' ', 'db_migration_v175', (select id from property where public_ID = 'paallysteluokka_paallystetty_tie'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Kivi', ' ', 'db_migration_v175', (select id from property where public_ID = 'paallysteluokka_paallystetty_tie'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 10, 'Kovat asfalttibetonit', ' ', 'db_migration_v175', (select id from property where public_ID = 'paallysteluokka_paallystetty_tie'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 20, 'Pehmeät asfalttibetonit', ' ', 'db_migration_v175', (select id from property where public_ID = 'paallysteluokka_paallystetty_tie'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 30, 'Soratien pintaus', ' ', 'db_migration_v175', (select id from property where public_ID = 'paallysteluokka_paallystetty_tie'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 40, 'Sorakulutuskerros', ' ', 'db_migration_v175', (select id from property where public_ID = 'paallysteluokka_paallystetty_tie'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 50, 'Muut pinnoitteet', ' ', 'db_migration_v175', (select id from property where public_ID = 'paallysteluokka_paallystetty_tie'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 99, 'Päällysteen tyyppi tuntematon', ' ', 'db_migration_v175', (select id from property where public_ID = 'paallysteluokka_paallystetty_tie'));