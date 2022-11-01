insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600073,300,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, VVH_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('lrm_position_primary_key_seq'), '52d58ce5-39e8-4ab4-8c43-d347a9945ab5:1', 1611317, 388553074, 103.000, 103.000, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600073, currval('lrm_position_primary_key_seq'));
insert into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values (621599, 600073, (select id from property where public_id='trafficSigns_info'), 'Add Info For Test', current_timestamp, 'dr2_test_data');
insert into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values (621600, 600073, (select id from property where public_id='trafficSigns_value'), '80', current_timestamp, 'dr2_test_data');
insert into single_choice_value(asset_id, enumerated_value_id, property_id) values (600073, (select id from enumerated_value where name_fi='C32 Nopeusrajoitus'), (select id from property where public_id='trafficSigns_type'));
UPDATE asset SET geometry = ST_GeomFromText('POINT(374467 6677347 0 0)',3067)
  WHERE id = 600073;