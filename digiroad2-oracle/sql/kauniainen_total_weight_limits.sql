insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY) values (11111, 30, current_timestamp, 'dr2_test_data');
INSERT INTO LRM_POSITION (ID, LINK_ID, VVH_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (22222, 'dd8bdb73-b8b4-4c81-a404-1126c4f4e714:1', 1611374, 1621077551, 0.000, 80.000, 1);
insert into asset_link (asset_id, position_id) values (11111, 22222);
insert into number_property_value(id, asset_id, property_id, value) values (nextval('primary_key_seq'), 11111, (select id from property where public_id = 'mittarajoitus'), 4000);

insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY, valid_to) values (11112, 30, current_timestamp, 'dr2_test_data', current_timestamp+INTERVAL'1 DAYS');
INSERT INTO LRM_POSITION (ID, LINK_ID, VVH_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (22223, 'dd8bdb73-b8b4-4c81-a404-1126c4f4e714:1', 1611374, 1621077551, 80.000, 120.000, 1);
insert into asset_link (asset_id, position_id) values (11112, 22223);
insert into number_property_value(id, asset_id, property_id, value) values (nextval('primary_key_seq'), 11112, (select id from property where public_id = 'mittarajoitus'), 8000);

insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY, valid_to) values (11113, 30, current_timestamp-INTERVAL'2 DAYS', 'dr2_test_data', current_timestamp-INTERVAL'1 DAYS');
INSERT INTO LRM_POSITION (ID, LINK_ID, VVH_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (22224, 'dd8bdb73-b8b4-4c81-a404-1126c4f4e714:1', 1611374, 1621077551, 120.000, 136.000, 1);
insert into asset_link (asset_id, position_id) values (11113, 22224);
insert into number_property_value(id, asset_id, property_id, value) values (nextval('primary_key_seq'), 11113, (select id from property where public_id = 'mittarajoitus'), 10000);

INSERT INTO asset (id, asset_type_id, CREATED_DATE, CREATED_BY) VALUES (11170, 30, current_timestamp-INTERVAL'2 DAYS', 'dr2_test_data');
INSERT INTO LRM_POSITION (ID, LINK_ID, VVH_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (27224, '1b10c3d0-0bcf-4467-aa7b-71c769918d10:1', 5361922, 1621077551, 120.000, 136.000, 1);
INSERT INTO asset_link (asset_id, position_id) VALUES (11170, 27224);
INSERT INTO number_property_value(id, asset_id, property_id, value) VALUES (nextval('primary_key_seq'), 11170, (SELECT id FROM property WHERE public_id = 'weight' AND asset_type_id = 30), 5000);