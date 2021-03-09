update single_choice_value
set enumerated_value_id = (select id from enumerated_value where property_id = (select id from property where public_id = 'tietojen_yllapitaja') and name_fi = 'ELY-keskus'),
modified_date = sysdate,
modified_by = 'db_migration_v5'
where property_id = (select id from property where public_id = 'tietojen_yllapitaja')
and enumerated_value_id = (select id from enumerated_value where property_id = (select id from property where public_id = 'tietojen_yllapitaja') and name_fi = 'Liikennevirasto');

delete from enumerated_value where property_id = (select id from property where public_id = 'tietojen_yllapitaja') and name_fi = 'Liikennevirasto';
