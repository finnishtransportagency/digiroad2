UPDATE enumerated_value
SET name_fi = 'Muu pysyvä esterakennelma', modified_date = current_timestamp, modified_by = 'db_migration_v1_43'
WHERE property_id = (SELECT id FROM property WHERE public_id = 'esterakennelma')
  AND value = 1;

INSERT INTO enumerated_value (id,property_id,value,name_fi,name_sv,image_id,created_date,created_by,modified_date,modified_by)  VALUES (nextval('primary_key_seq'), (select id from property where public_id = 'esterakennelma'),3,E'Kaivanne',NULL,NULL,current_timestamp,E'db_migration_v1_43',NULL,NULL);
INSERT INTO enumerated_value (id,property_id,value,name_fi,name_sv,image_id,created_date,created_by,modified_date,modified_by)  VALUES (nextval('primary_key_seq'), (select id from property where public_id = 'esterakennelma'),99,E'Ei tiedossa',NULL,NULL,current_timestamp,E'db_migration_v1_43',NULL,NULL);