insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600049,230,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (70000012, 1611317, 388553074, 103.000, 103.000, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600049, 70000012);
insert into single_choice_value(asset_id, enumerated_value_id, property_id) values (600049, (select id from enumerated_value where name_fi='Valo/äänimerkki'), (select id from property where public_id='turvavarustus'));
insert into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values (nextval('primary_key_seq'), 600049, (select id from property where public_id='tasoristeystunnus'), 'test_code', current_timestamp, 'dr2_test_data');
UPDATE asset SET geometry = ST_GeomFromText('POINT(374467 6677347 0 0)',3067)
  WHERE id = 600049;

insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600050,230,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (70000013, 1611341, 388553080, 69.000, 69.000, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600050, 70000013);
insert into single_choice_value(asset_id, enumerated_value_id, property_id) values (600050, (select id from enumerated_value where name_fi='Puolipuomi'), (select id from property where public_id='turvavarustus'));
insert into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values (nextval('primary_key_seq'), 600050, (select id from property where public_id='tasoristeystunnus'), 'test_code', current_timestamp, 'dr2_test_data');
UPDATE asset SET geometry = ST_GeomFromText('POINT(374443.764141219 6677245.28337185 0 0)',3067)
  WHERE id = 600050;

insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600051,230,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (70000014, 12345, 12345, 103.000, 103.000, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600051, 70000014);
insert into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values (600052, 600051, (select id from property where public_id='rautatien_tasoristeyksen_nimi'), 'Hyvä nimi', current_timestamp, 'dr2_test_data');
insert into single_choice_value(asset_id, enumerated_value_id, property_id) values (600051, (select id from enumerated_value where name_fi='Valo/äänimerkki'), (select id from property where public_id='turvavarustus'));
insert into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values (nextval('primary_key_seq'), 600051, (select id from property where public_id='tasoristeystunnus'), 'test_code', current_timestamp, 'dr2_test_data');
UPDATE asset SET geometry = ST_GeomFromText('POINT(374396 6677319 0 0)',3067)
  WHERE id = 600051;