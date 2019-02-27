create table date_period_value (
	id number primary key,
	asset_id number references asset not null,
	property_id references property not null,
	start_date TIMESTAMP not null,
	end_date TIMESTAMP not null
);

insert into localized_string (id, value_fi, value_sv, created_by, created_date)
values (primary_key_seq.nextval, 'Kelirikkokausi', NULL, 'db_migration_v196', sysdate);

insert into property(id, asset_type_id, property_type, required, created_by, public_id, name_localized_string_id)
values (primary_key_seq.nextval, 130, 'date_period', '0', 'db_migration_v196', 'spring_thaw_period', (select id from localized_string where value_fi = 'Kelirikkokausi' and created_by = 'db_migration_v196'));

insert into localized_string (id, value_fi, value_sv, created_by, created_date)
values (primary_key_seq.nextval, 'Jokavuotinen', NULL, 'db_migration_v196', sysdate);

insert into property(id, asset_type_id, property_type, required, created_by, public_id, name_localized_string_id)
values (primary_key_seq.nextval, 130, 'checkbox', '0', 'db_migration_v196', 'annual_repetition', (select id from localized_string where value_fi = 'Jokavuotinen' and created_by = 'db_migration_v196'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (primary_key_seq.nextval, 1, 'Jokavuotinen', ' ', 'db_migration_v196', (select id from property where public_id = 'annual_repetition'));

insert into enumerated_value (id, value, name_fi, name_sv, created_by, property_id)
values (primary_key_seq.nextval, 0, 'Ei toistu', ' ', 'db_migration_v196', (select id from property where public_id = 'annual_repetition'));