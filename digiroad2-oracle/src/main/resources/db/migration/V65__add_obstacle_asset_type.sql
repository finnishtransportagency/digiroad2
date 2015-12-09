insert into asset_type (id, name, geometry_type) values(220, 'Esterakennelma', 'point');

insert into property (id, asset_type_id, required, created_by, public_id, property_type)
values (primary_key_seq.nextval, 220, '0', 'db_migration_v65', 'Esterakennelma', 'single_choice');

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 1, 'Suljettu yhteys', 'db_migration_v65', (select id from property where public_id = 'Esterakennelma'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 2, 'Avattava puomi', 'db_migration_v65', (select id from property where public_id = 'Esterakennelma'));