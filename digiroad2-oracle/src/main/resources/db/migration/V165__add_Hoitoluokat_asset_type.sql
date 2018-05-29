INSERT INTO ASSET_TYPE (ID, NAME, GEOMETRY_TYPE, CREATED_BY)
VALUES (380, 'Hoitoluokat', 'linear', 'db_migration_v165');

-- Talvihoitoluokka
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Talvihoitoluokka','db_migration_v165', sysdate);

INSERT INTO PROPERTY (ID,ASSET_TYPE_ID,PROPERTY_TYPE, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Hoitoluokat'),'single_choice', 'db_migration_v165', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Talvihoitoluokka' AND CREATED_BY='db_migration_v165'),'hoitoluokat_talvihoitoluokka');

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 0, '(IsE) Liukkaudentorjunta ilman toimenpideaikaa', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_talvihoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, '(Is) Normaalisti aina paljaana', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_talvihoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, '(I) Normaalisti paljaana', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_talvihoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, '(Ib) Pääosin suolattava, ajoittain hieman liukas', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_talvihoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, '(Ic) Pääosin hiekoitettava, ohut lumipolanne sallittu', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_talvihoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, '(II) Pääosin lumipintainen', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_talvihoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 6, '(III) Pääosin lumipintainen, pisin toimenpideaika', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_talvihoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 7, '(L) Kevyen liikenteen laatukäytävät', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_talvihoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 8, '(K1) Melko vilkkaat kevyen liikenteen väylät', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_talvihoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 9, '(K2) Kevyen liikenteen väylien perus talvihoitotaso', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_talvihoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 10, '(ei talvih.) Kevyen liikenteen väylät, joilla ei talvihoitoa', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_talvihoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 20, 'Pääkadut ja vilkkaat väylät', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_talvihoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 30, 'Kokoojakadut', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_talvihoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 40, 'Tonttikadut', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_talvihoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 50, 'A-luokan väylät', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_talvihoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 60, 'B-luokan väylät', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_talvihoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 70, 'C-luokan väylät', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_talvihoitoluokka'));


--Viherhoitoluokka
INSERT INTO LOCALIZED_STRING (ID,VALUE_FI, CREATED_BY, CREATED_DATE)
VALUES (primary_key_seq.nextval,'Viherhoitoluokka','db_migration_v165', sysdate);

INSERT INTO PROPERTY (ID,ASSET_TYPE_ID,PROPERTY_TYPE, CREATED_BY, NAME_LOCALIZED_STRING_ID, PUBLIC_ID)
VALUES (primary_key_seq.nextval, (select id from asset_type where name = 'Hoitoluokat'),'single_choice', 'db_migration_v165', (SELECT ID FROM LOCALIZED_STRING WHERE VALUE_FI = 'Viherhoitoluokka' AND CREATED_BY='db_migration_v165'),'hoitoluokat_viherhoitoluokka');

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 1, '(N1) 2-ajorataiset tiet', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_viherhoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 2, '(N2) Valta- ja kantatiet sekä vilkkaat seututiet', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_viherhoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 3, '(N3) Muut tiet', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_viherhoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 4, '(T1) Puistomainen taajamassa', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_viherhoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 5, '(T2) Luonnonmukainen taajamassa', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_viherhoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 6, '(E1) Puistomainen erityisalue', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_viherhoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 7, '(E2) Luonnonmukainen erityisalue', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_viherhoitoluokka'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 8, '(Y) Ympäristötekijä', ' ', 'db_migration_v165', (select id from property where public_ID = 'hoitoluokat_viherhoitoluokka'));

