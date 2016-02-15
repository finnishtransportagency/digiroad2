insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600046,220,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, START_MEASURE, END_MEASURE, MML_ID) VALUES (70000009, 1611317, 103.000, 103.000, 388553074);
insert into asset_link (ASSET_ID, POSITION_ID) values (600046, 70000009);
insert into single_choice_value(asset_id, enumerated_value_id, property_id) values (600046, (select id from enumerated_value where name_fi='Suljettu yhteys'), (select id from property where public_id='esterakennelma'));
UPDATE asset
  SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                    3067,
                                    NULL,
                                    MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                    MDSYS.SDO_ORDINATE_ARRAY(374467, 6677347, 0, 0)
                                   )
  WHERE id = 600046;


insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600047,220,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, START_MEASURE, END_MEASURE, MML_ID) VALUES (70000010, 1611341, 69.000, 69.000, 388553080);
insert into asset_link (ASSET_ID, POSITION_ID) values (600047, 70000010);
insert into single_choice_value(asset_id, enumerated_value_id, property_id) values (600047, (select id from enumerated_value where name_fi='Avattava puomi'), (select id from property where public_id='esterakennelma'));
UPDATE asset
  SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                    3067,
                                    NULL,
                                    MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                    MDSYS.SDO_ORDINATE_ARRAY(374443.764141219, 6677245.28337185, 0, 0)
                                   )
  WHERE id = 600047;


insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600048,220,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, START_MEASURE, END_MEASURE, MML_ID) VALUES (70000011, null, 100.000, 100.000, 12345);
insert into asset_link (ASSET_ID, POSITION_ID) values (600048, 70000011);
insert into single_choice_value(asset_id, enumerated_value_id, property_id) values (600048, (select id from enumerated_value where name_fi='Avattava puomi'), (select id from property where public_id='esterakennelma'));
UPDATE asset
  SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                    3067,
                                    NULL,
                                    MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                    MDSYS.SDO_ORDINATE_ARRAY(374396, 6677319 , 0, 0)
                                   )
  WHERE id = 600048;
