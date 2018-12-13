INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 125 , 'Sivutien risteys - molemmilta', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 126 , 'Sivutien risteys - vasemalta', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 127 , 'Sivutien risteys - takaviistosta', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 128 , 'Rautatien tasoristeys ilman puomeja', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'))

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 129 , 'Rautatien tasoristeys, jossa on puomit', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 130 , 'Yksiraiteisen rautatien tasoristeys', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 131 , 'Kaksi tai useampiraiteisen rautatien tasoristeys', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 132 , 'Vuoropysäköinti kielletty parillisina päivinä', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 133 , 'Moottorikelkkailureitti', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 134 , 'Ratsastustie', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 135 , 'Liityntäpysäköintipaikka', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 136 , 'Etäisyys pakolliseen pysäyttämiseen', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 137 , 'Sähköjohdon korkeus', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 138 , 'Vaikutusalue molempiin suuntiin', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 139 , 'Vaikutusalue molempiin suuntiin (pystysuora)', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 140 , 'Vaikutusalue nuolen suuntaan', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 141 , 'Vaikutusalue alkaa', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 142 , 'Vaikutusalue päättyy', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 143 , 'Voimassaoloaika', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 144 , 'Etuajo-oikeutetun liikenteen suunta', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 145 , 'Tukkitie', ' ' , 'db_migration_v190',  (select id from property where public_ID = 'trafficSigns_type'));
