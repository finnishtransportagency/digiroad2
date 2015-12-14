insert into asset_type (id, name, geometry_type) values(230, 'Rautatien tasoristeys', 'point');

insert into property (id, asset_type_id, required, created_by, public_id, property_type)
values (primary_key_seq.nextval, 230, '0', 'db_migration_v66', 'turvavarustus', 'single_choice');

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 1, 'Rautatie ei käytössä', 'db_migration_v66', (select id from property where public_id = 'turvavarustus'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 2, 'Ei turvalaitetta', 'db_migration_v66', (select id from property where public_id = 'turvavarustus'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 3, 'Valo/äänimerkki', 'db_migration_v66', (select id from property where public_id = 'turvavarustus'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 4, 'Puolipuomi', 'db_migration_v66', (select id from property where public_id = 'turvavarustus'));

insert into enumerated_value (id, value, name_fi, created_by, property_id)
values (primary_key_seq.nextval, 5, 'Kokopuomi', 'db_migration_v66', (select id from property where public_id = 'turvavarustus'));

insert into property (id, asset_type_id, created_by, public_id, property_type)
values (primary_key_seq.nextval, 230, 'db_migration_v66', 'Nimi', 'text');

