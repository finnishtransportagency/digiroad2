INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
VALUES (290, 'Huoltotie', 'linear', 'db_migration_v101');

-- Käyttöoikeus
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Käyttöoikeus','db_migration_v101', sysdate);

INSERT INTO PROPERTY (ID,ASSET_TYPE_ID,PROPERTY_TYPE,REQUIRED, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Huoltotie'),'single_choice',1, 'db_migration_v101', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Käyttöoikeus' AND CREATED_BY='db_migration_v101'),'huoltotie_kayttooikeus');

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'Tieoikeus', ' ', 'db_migration_v101', (select id from property where public_ID = 'huoltotie_kayttooikeus'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Tiekunnan osakkuus', ' ', 'db_migration_v101', (select id from property where public_ID = 'huoltotie_kayttooikeus'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, 'LiVin hallinnoimalla maa-alueella', ' ', 'db_migration_v101', (select id from property where public_ID = 'huoltotie_kayttooikeus'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, 'Huoltoreittikäytössä olevat kevyen liikenteen väylät (ei rautatieliikennealuetta) väylä', ' ', 'db_migration_v101', (select id from property where public_ID = 'huoltotie_kayttooikeus'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, 'Tuntematon', ' ', 'db_migration_v101', (select id from property where public_ID = 'huoltotie_kayttooikeus'));

--Huoltovastuu
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Huoltovastuu','db_migration_v101', sysdate);

INSERT INTO PROPERTY (ID,ASSET_TYPE_ID,PROPERTY_TYPE,REQUIRED, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Huoltotie'), 'single_choice', 1, 'db_migration_v101', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Huoltovastuu' AND CREATED_BY='db_migration_v101'), 'huoltotie_huoltovastuu');

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, 'LiVi', ' ', 'db_migration_v101', (select id from property where public_ID = 'huoltotie_huoltovastuu'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, 'Muu', ' ', 'db_migration_v101', (select id from property where public_ID = 'huoltotie_huoltovastuu'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, 'Ei tietoa', ' ', 'db_migration_v101', (select id from property where public_ID = 'huoltotie_huoltovastuu'));

--Add property Tiehoitokunta
INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Tiehoitokunta ','db_migration_v101', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Huoltotie'), 'text', 1, 'db_migration_v101', 'huoltotie_tiehoitokunta', (select id from LOCALIZED_STRING where VALUE_FI = 'Tiehoitokunta '));

--Add property Nimi
INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Nimi','db_migration_v101', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Huoltotie'), 'text', 0, 'db_migration_v101', 'huoltotie_nimi', (select id from LOCALIZED_STRING where VALUE_FI = 'Nimi'));

--Add property Osoite
INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Osoite','db_migration_v101', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Huoltotie'), 'text', 0, 'db_migration_v101', 'huoltotie_osoite', (select id from LOCALIZED_STRING where VALUE_FI = 'Osoite'));

--Add property Postinumero
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Postinumero','db_migration_v101', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Huoltotie'), 'text', 0, 'db_migration_v101', 'huoltotie_postinumero', (select id from LOCALIZED_STRING where VALUE_FI = 'Postinumero'));

--Add property Postitoimipaikka
INSERT INTO LOCALIZED_STRING  (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Postitoimipaikka','db_migration_v101', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Huoltotie'), 'text', 0, 'db_migration_v101', 'huoltotie_postitoimipaikka', (select id from LOCALIZED_STRING where VALUE_FI = 'Postitoimipaikka'));

--Add property Puhelin 1
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Puhelin 1','db_migration_v101', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Huoltotie'), 'text', 0, 'db_migration_v101', 'huoltotie_puh1', (select id from LOCALIZED_STRING where VALUE_FI = 'Puhelin 1'));

--Add property Puhelin 2
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Puhelin 2','db_migration_v101', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Huoltotie'), 'text', 0, 'db_migration_v101', 'huoltotie_puh2', (select id from LOCALIZED_STRING where VALUE_FI = 'Puhelin 2'));

--Add property Lisätietoa
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Lisätietoa','db_migration_v101', sysdate);

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Huoltotie'), 'text', 0, 'db_migration_v101', 'huoltotie_lisatieto', (select id from LOCALIZED_STRING where VALUE_FI = 'Lisätietoa'));
