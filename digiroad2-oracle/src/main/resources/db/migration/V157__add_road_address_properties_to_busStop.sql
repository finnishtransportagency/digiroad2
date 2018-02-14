insert into localized_string (id, value_fi, value_sv, created_by, created_date) values(primary_key_seq.nextval, 'Tie', '', 'db_migration_v157', sysdate);

insert into localized_string (id, value_fi, value_sv, created_by, created_date) values(primary_key_seq.nextval, 'Osa', '', 'db_migration_v157', sysdate);

insert into localized_string (id, value_fi, value_sv, created_by, created_date) values(primary_key_seq.nextval, 'Aet', '', 'db_migration_v157', sysdate);

insert into localized_string (id, value_fi, value_sv, created_by, created_date) values(primary_key_seq.nextval, 'Ajr', '', 'db_migration_v157', sysdate);

insert into localized_string (id, value_fi, value_sv, created_by, created_date) values(primary_key_seq.nextval, 'Puoli', '', 'db_migration_v157', sysdate);




insert into property (id, asset_type_id, created_by, name_localized_string_id, property_type, public_id)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), 'db_migration_v157', (select id from localized_string where value_fi = 'Tie'), 'read_only_number', 'tie');

insert into property (id, asset_type_id, created_by, name_localized_string_id, property_type, public_id)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), 'db_migration_v157', (select id from localized_string where value_fi = 'Osa'), 'read_only_number', 'osa');

insert into property (id, asset_type_id, created_by, name_localized_string_id, property_type, public_id)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), 'db_migration_v157', (select id from localized_string where value_fi = 'Aet'), 'read_only_number', 'aet');

insert into property (id, asset_type_id, created_by, name_localized_string_id, property_type, public_id)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), 'db_migration_v157', (select id from localized_string where value_fi = 'Ajr'), 'read_only_number', 'ajr');

insert into property (id, asset_type_id, created_by, name_localized_string_id, property_type, public_id)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), 'db_migration_v157', (select id from localized_string where value_fi = 'Puoli'), 'read_only_number', 'puoli');