INSERT INTO enumerated_value (id,property_id,value,name_fi,name_sv,image_id,created_date,created_by,modified_date,modified_by)
VALUES (nextval('primary_key_seq'), (select id from property where public_id = 'trafficSigns_type'),402,'H12.14 Kevyt sähköajoneuvo',NULL,NULL,current_timestamp,'db_migration_v1_58',NULL,NULL);

INSERT INTO enumerated_value (id,property_id,value,name_fi,name_sv,image_id,created_date,created_by,modified_date,modified_by)
VALUES (nextval('primary_key_seq'), (select id from property where public_id = 'trafficSigns_type'),403,'H27 Ylijatkettu jalkakäytävä',NULL,NULL,current_timestamp,'db_migration_v1_58',NULL,NULL);