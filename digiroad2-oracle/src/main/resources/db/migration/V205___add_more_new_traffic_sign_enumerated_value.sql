INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 150 , 'Matkailuajoneuvo', ' ' , 'db_migration_v205',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval, 151 , 'Mopo', ' ' , 'db_migration_v205',  (select id from property where public_ID = 'trafficSigns_type'));
