insert into asset_type (id, name, geometry_type) values(240, 'Opastustaulu ja sen informaatio', 'point');

insert into property (id, asset_type_id, created_by, public_id, property_type)
values (primary_key_seq.nextval, 240, 'db_migration_v67', 'opastustaulun_teksti', 'text');
