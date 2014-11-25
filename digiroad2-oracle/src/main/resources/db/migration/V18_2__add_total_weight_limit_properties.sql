insert into property (id, public_id, asset_type_id, created_by, name_localized_string_id, property_type, ui_position_index)
values (primary_key_seq.nextval, 'kokonaispainorajoitus', (select id from asset_type where name = 'Kokonaispainorajoitukset'), 'db_migration_v4', (select id from localized_string where value_fi = 'Rajoitus'), 'text', 30);
