insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 90, '90', '', (select id from property where public_id = 'rajoitus'), 'db_migration_v4', sysdate);
