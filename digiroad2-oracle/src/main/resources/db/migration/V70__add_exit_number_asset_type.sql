insert into asset_type (id, name, geometry_type) values(270, 'Liittymänumero', 'linear');

insert into property (id, asset_type_id, created_by, public_id, property_type)
values (primary_key_seq.nextval, 270, 'db_migration_v69', 'liittymänumero', 'text');