delete from enumerated_value where property_id in (select id from property where asset_type_id = 410);
delete from property where asset_type_id = 410;
delete from localized_string where value_fi = 'Hirvivaro';
delete from asset_type where id = 410;