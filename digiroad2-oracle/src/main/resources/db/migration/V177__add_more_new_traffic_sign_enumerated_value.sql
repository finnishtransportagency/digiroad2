INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval,  45 , 'Vapaa Leveys', ' ' , 'db_migration_v177',  (select id from property where public_ID = 'trafficSigns_type'));
