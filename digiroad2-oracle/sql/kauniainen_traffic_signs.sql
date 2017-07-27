insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600073,300,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (lrm_position_primary_key_seq.nextval, 1611317, 388553074, 103.000, 103.000, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600073, lrm_position_primary_key_seq.currval);
insert into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values (621599, 600073, (select id from property where public_id='trafficSigns_info'), 'Add Info For Test', sysdate, 'dr2_test_data');
insert into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values (621600, 600073, (select id from property where public_id='trafficSigns_value'), '80', sysdate, 'dr2_test_data');
insert into single_choice_value(asset_id, enumerated_value_id, property_id) values (600073, (select id from enumerated_value where name_fi='Nopeusrajoitus'), (select id from property where public_id='trafficSigns_type'));
UPDATE asset
  SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                    3067,
                                    NULL,
                                    MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                    MDSYS.SDO_ORDINATE_ARRAY(374467, 6677347, 0, 0)
                                   )
  WHERE id = 600073;