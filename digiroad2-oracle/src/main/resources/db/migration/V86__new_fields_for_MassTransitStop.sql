-- Insert two new fields at form MassTransitSop
-- Address field in Finnish and Swedish

INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_DATE)
    VALUES (primary_key_seq.nextval, 'Osoite suomeksi', sysdate);
    
INSERT INTO LOCALIZED_STRING (ID, VALUE_FI, CREATED_DATE)  
    VALUES (primary_key_seq.nextval, 'Osoite ruotsiksi', sysdate);


INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID) 
    VALUES (primary_key_seq.nextval, 10, 'text', 0, 'db_migration_v86', 'osoite_suomeksi', (select id from LOCALIZED_STRING where VALUE_FI = 'Osoite suomeksi')); 

INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID) 
    VALUES (primary_key_seq.nextval, 10, 'text', 0, 'db_migration_v86', 'osoite_ruotsiksi', (select id from LOCALIZED_STRING where VALUE_FI = 'Osoite ruotsiksi'));