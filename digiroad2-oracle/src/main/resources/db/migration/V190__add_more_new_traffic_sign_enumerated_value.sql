INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 162 , 'Sivutien risteys - molemmilta', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 163 , 'Sivutien risteys - vasemalta', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 164 , 'Sivutien risteys - takaviistosta', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 171 , 'Rautatien tasoristeys ilman puomeja', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 172 , 'Rautatien tasoristeys, jossa on puomit', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 176 , 'Yksiraiteisen rautatien tasoristeys', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 177 , 'Kaksi tai useampiraiteisen rautatien tasoristeys', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 382 , 'Vuoropysäköinti kielletty parillisina päivinä', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 426 , 'Moottorikelkkailureitti', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 427 , 'Ratsastustie', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 520 , 'Liityntäpysäköintipaikka', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 816 , 'Etäisyys pakolliseen pysäyttämiseen', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 823 , 'Sähköjohdon korkeus', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 824 , 'Vaikutusalue molempiin suuntiin', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 825 , 'Vaikutusalue molempiin suuntiin (pystysuora)', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 826 , 'Vaikutusalue nuolen suuntaan', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 827 , 'Vaikutusalue alkaa', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 828 , 'Vaikutusalue päättyy', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 853 , 'Voimassaoloaika', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 861 , 'Etuajo-oikeutetun liikenteen suunta', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 862 , 'Tukkitie', ' ' , 'db_migration_v183',  (select id from property where public_ID = 'trafficSigns_type'));
