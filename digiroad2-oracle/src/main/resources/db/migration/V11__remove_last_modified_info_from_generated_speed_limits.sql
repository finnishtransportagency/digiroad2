update asset set modified_by = null, modified_date = null
where modified_by = 'automatic_speed_limit_generation' and asset_type_id = 20;