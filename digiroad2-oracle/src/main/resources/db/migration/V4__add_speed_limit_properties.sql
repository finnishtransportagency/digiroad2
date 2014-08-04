insert into localized_string (id, value_fi, value_sv, created_by, created_date) values(primary_key_seq.nextval, 'Rajoitus', '', 'db_migration_v4', sysdate);

insert into property (id, asset_type_id, created_by, name_localized_string_id, property_type, ui_position_index)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Nopeusrajoitukset'), 'db_migration_v4', (select id from localized_string where value_fi = 'Rajoitus'), 'single_choice', 30);

insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 20, '20', '', (select property.id from property, localized_string where localized_string.value_fi = 'Rajoitus' and property.NAME_LOCALIZED_STRING_ID = localized_string.id), 'db_migration_v4', sysdate);

insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 30, '30', '', (select property.id from property, localized_string where localized_string.value_fi = 'Rajoitus' and property.NAME_LOCALIZED_STRING_ID = localized_string.id), 'db_migration_v4', sysdate);

insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 40, '40', '', (select property.id from property, localized_string where localized_string.value_fi = 'Rajoitus' and property.NAME_LOCALIZED_STRING_ID = localized_string.id), 'db_migration_v4', sysdate);

insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 50, '50', '', (select property.id from property, localized_string where localized_string.value_fi = 'Rajoitus' and property.NAME_LOCALIZED_STRING_ID = localized_string.id), 'db_migration_v4', sysdate);

insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 60, '60', '', (select property.id from property, localized_string where localized_string.value_fi = 'Rajoitus' and property.NAME_LOCALIZED_STRING_ID = localized_string.id), 'db_migration_v4', sysdate);

insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 70, '70', '', (select property.id from property, localized_string where localized_string.value_fi = 'Rajoitus' and property.NAME_LOCALIZED_STRING_ID = localized_string.id), 'db_migration_v4', sysdate);

insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 80, '80', '', (select property.id from property, localized_string where localized_string.value_fi = 'Rajoitus' and property.NAME_LOCALIZED_STRING_ID = localized_string.id), 'db_migration_v4', sysdate);

insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 100, '100', '', (select property.id from property, localized_string where localized_string.value_fi = 'Rajoitus' and property.NAME_LOCALIZED_STRING_ID = localized_string.id), 'db_migration_v4', sysdate);

insert into enumerated_value (id, value, name_fi, name_sv, property_id, created_by, created_date)
values (primary_key_seq.nextval, 120, '120', '', (select property.id from property, localized_string where localized_string.value_fi = 'Rajoitus' and property.NAME_LOCALIZED_STRING_ID = localized_string.id), 'db_migration_v4', sysdate);
