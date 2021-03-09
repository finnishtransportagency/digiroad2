insert into asset_type (id, name, geometry_type) values(260, 'Eurooppatienumero', 'linear');

insert into property (id, asset_type_id, created_by, public_id, property_type)
values (primary_key_seq.nextval, 260, 'db_migration_v69', 'eurooppatienumero', 'text');