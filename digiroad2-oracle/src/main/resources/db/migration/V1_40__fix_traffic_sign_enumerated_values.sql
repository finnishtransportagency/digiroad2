UPDATE enumerated_value
SET name_fi = 'Epätosi', modified_date = current_timestamp, modified_by = 'db_migration_v1_40'
WHERE property_id = (SELECT id FROM property WHERE public_id = 'old_traffic_code')
AND value = 0;

UPDATE enumerated_value
SET name_fi = 'Tosi', modified_date = current_timestamp, modified_by = 'db_migration_v1_40'
WHERE property_id = (SELECT id FROM property WHERE public_id = 'old_traffic_code')
AND value = 1;