DECLARE
    localized_string_id PLS_INTEGER;
BEGIN
    localized_string_id := primary_key_seq.nextval;

    insert into localized_string (id, value_fi, value_sv, created_by, created_date) values(localized_string_id, 'Rajoitus', '', 'db_migration_v4', sysdate);

    insert into property (id, public_id, asset_type_id, created_by, name_localized_string_id, property_type, ui_position_index)
    values (primary_key_seq.nextval, 'kokonaispainorajoitus', (select id from asset_type where name = 'Kokonaispainorajoitukset'), 'db_migration_v4', localized_string_id, 'text', 30);
END;
