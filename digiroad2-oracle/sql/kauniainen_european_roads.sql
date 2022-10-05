insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY) values (600066, 260, current_timestamp, 'dr2_test_data');
insert into lrm_position (ID, MML_ID, VVH_ID, link_id, START_MEASURE, END_MEASURE, SIDE_CODE) values (70000018, 388552666, 1611395, 'fce6f5fd-4f59-4ca1-abc4-b73f38ba9087:1', 0, 99.483, 1);
insert into asset_link (asset_id, position_id) values (600066, 70000018);
insert into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values (600067, 600066, (select id from property where public_id='eurooppatienumero'), 'E666' || chr(10) || 'E667', current_timestamp, 'dr2_test_data')
