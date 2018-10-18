INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval,  125 , 'Hirvieläimiä', ' ' , 'db_migration_v185',  (select id from property where public_ID = 'trafficSigns_type'));

INSERT INTO ENUMERATED_VALUE (ID, VALUE, NAME_FI, NAME_SV, CREATED_BY, PROPERTY_ID)
VALUES (primary_key_seq.nextval,  126 , 'Poroja', ' ' , 'db_migration_v185',  (select id from property where public_ID = 'trafficSigns_type'));