update property set required = 1, modified_date = sysdate, modified_by = 'db_migration_v6' where public_id = 'nimi_suomeksi';

update property set required = 0, modified_date = sysdate, modified_by = 'db_migration_v6' where public_id = 'tietojen_yllapitaja';
update property set required = 0, modified_date = sysdate, modified_by = 'db_migration_v6' where public_id = 'katos';