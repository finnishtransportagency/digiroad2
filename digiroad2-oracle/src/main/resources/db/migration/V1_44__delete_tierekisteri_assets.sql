delete from enumerated_value where property_id in (select id from property where asset_type_id in (310, 320, 330, 340, 350, 360, 370));
delete from property where asset_type_id in (310, 320, 330, 340, 350, 360, 370);
delete from asset_type where id in (310, 320, 330, 340, 350, 360, 370);