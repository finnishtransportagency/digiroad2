INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 6, 'Muu sopimus', ' ', 'db_migration_v160', (select id from property where public_ID = 'huoltotie_kayttooikeus'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 9, 'Potentiaalinen kayttooikeus', ' ', 'db_migration_v160', (select id from property where public_ID = 'huoltotie_kayttooikeus'));
