insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY) values (11111, 30, SYSDATE, 'dr2_test_data');
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (22222, null, 388562360, 0.000, 80.000, 1);
insert into asset_link (asset_id, position_id) values (11111, 22222);
insert into number_property_value(id, asset_id, property_id, value) values (primary_key_seq.nextval, 11111, (select id from property where public_id = 'mittarajoitus'), 4000);

insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY, valid_to) values (11112, 30, SYSDATE, 'dr2_test_data', SYSDATE+1);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (22223, null, 388562360, 80.000, 120.000, 1);
insert into asset_link (asset_id, position_id) values (11112, 22223);
insert into number_property_value(id, asset_id, property_id, value) values (primary_key_seq.nextval, 11112, (select id from property where public_id = 'mittarajoitus'), 8000);

insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY, valid_to) values (11113, 30, SYSDATE-2, 'dr2_test_data', SYSDATE-1);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (22224, null, 388562360, 120.000, 136.000, 1);
insert into asset_link (asset_id, position_id) values (11113, 22224);
insert into number_property_value(id, asset_id, property_id, value) values (primary_key_seq.nextval, 11113, (select id from property where public_id = 'mittarajoitus'), 10000);
