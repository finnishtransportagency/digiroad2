alter table asset drop column validity_direction;

insert into localized_string (id, value_fi, value_sv, created_by, created_date) values(primary_key_seq.nextval, 'Rajoitus', '', 'db_migration_v4', sysdate);

insert into property (id, public_id, asset_type_id, created_by, name_localized_string_id, property_type, ui_position_index)
values (primary_key_seq.nextval, 'rajoitus', (select id from asset_type where name = 'Nopeusrajoitukset'), 'db_migration_v4', (select id from localized_string where value_fi = 'Rajoitus'), 'single_choice', 30);

insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 20, '20', '', (select id from property where public_id = 'rajoitus'), 'db_migration_v4', sysdate);

insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 30, '30', '', (select id from property where public_id = 'rajoitus'), 'db_migration_v4', sysdate);

insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 40, '40', '', (select id from property where public_id = 'rajoitus'), 'db_migration_v4', sysdate);

insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 50, '50', '', (select id from property where public_id = 'rajoitus'), 'db_migration_v4', sysdate);

insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 60, '60', '', (select id from property where public_id = 'rajoitus'), 'db_migration_v4', sysdate);

insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 70, '70', '', (select id from property where public_id = 'rajoitus'), 'db_migration_v4', sysdate);

insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 80, '80', '', (select id from property where public_id = 'rajoitus'), 'db_migration_v4', sysdate);

insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 100, '100', '', (select id from property where public_id = 'rajoitus'), 'db_migration_v4', sysdate);

insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 120, '120', '', (select id from property where public_id = 'rajoitus'), 'db_migration_v4', sysdate);
