delete from single_choice_value where asset_id in (SELECT id from asset where asset_type_id = 20 and created_by = 'automatic_speed_limit_generation' and modified_by is null);
delete from asset_link where asset_id in (SELECT id from asset where asset_type_id = 20 and created_by = 'automatic_speed_limit_generation' and modified_by is null);
delete from lrm_position where id not in (select position_id from asset_link);
delete from asset where asset_type_id = 20 and created_by = 'automatic_speed_limit_generation' and modified_by is null;