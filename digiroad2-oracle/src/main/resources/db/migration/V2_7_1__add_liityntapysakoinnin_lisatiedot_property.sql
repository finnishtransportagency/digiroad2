insert into localized_string (id, value_fi, value_sv, created_by, created_date) values(primary_key_seq.nextval, 'Liityntäpysäköinnin lisätiedot', '', 'db_migration_v2.7.1', sysdate);

insert into property (id, asset_type_id, created_by, name_localized_string_id, property_type, ui_position_index)
values (primary_key_seq.nextval, (select id from asset_type where name = 'Bussipysäkit'), 'db_migration_v2.7.1', (select id from localized_string where value_fi = 'Liityntäpysäköinnin lisätiedot'), 'text', 135);