insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY) values (11111, 30, SYSDATE, 'dr2_test_data');
insert into lrm_position (ID, ROAD_LINK_ID, START_MEASURE, END_MEASURE, SIDE_CODE) values (22222, 6551, 20, 100, 1);
insert into asset_link (asset_id, position_id) values (11111, 22222);
insert into number_property_value(id, asset_id, property_id, value) values (primary_key_seq.nextval, 11111, (select id from property where public_id = 'kokonaispainorajoitus'), 4000);
