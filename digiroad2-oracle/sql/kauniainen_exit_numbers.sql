insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY) values (600068, 270, current_timestamp, 'dr2_test_data');
insert into lrm_position (ID, MML_ID, link_id, START_MEASURE, END_MEASURE, SIDE_CODE) values (70000019, 388552666, 1611395, 0, 99.483, 1);
insert into asset_link (asset_id, position_id) values (600068, 70000019);
insert into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values (600069, 600068, (select id from property where public_id='liittymänumero'), '20' || chr(10) || '21', current_timestamp, 'dr2_test_data')
