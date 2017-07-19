insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600073,300,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (70000655, 1611317, 388553074, 103.000, 103.000, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600073, 70000655);
insert into single_choice_value(asset_id, enumerated_value_id, property_id) values (600073, (select id from enumerated_value where name_fi='Nopeusrajoitus'), (select id from property where public_id='liikennemerkki_tyyppi'));
UPDATE asset
  SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                    3067,
                                    NULL,
                                    MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                    MDSYS.SDO_ORDINATE_ARRAY(374467, 6677347, 0, 0)
                                   )
  WHERE id = 600073;


insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600074,300,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (70000656, 1611341, 388553080, 69.000, 69.000, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600074, 70000656);
insert into single_choice_value(asset_id, enumerated_value_id, property_id) values (600074, (select id from enumerated_value where name_fi='Puolipuomi'), (select id from property where public_id='turvavarustus'));
UPDATE asset
   SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                    3067,
                                    NULL,
                                    MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                    MDSYS.SDO_ORDINATE_ARRAY(374443.764141219, 6677245.28337185, 0, 0)
                                   )
  WHERE id = 600074;


insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600075,300,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (70000657, 12345, 12345, 103.000, 103.000, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600075, 70000657);
insert into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values (600052, 600075, (select id from property where public_id='rautatien_tasoristeyksen_nimi'), 'Hyvä nimi', sysdate, 'dr2_test_data');
insert into single_choice_value(asset_id, enumerated_value_id, property_id) values (600075, (select id from enumerated_value where name_fi='Valo/äänimerkki'), (select id from property where public_id='turvavarustus'));
UPDATE asset
   SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                    3067,
                                    NULL,
                                    MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                    MDSYS.SDO_ORDINATE_ARRAY(374396, 6677319 , 0, 0)
                                   )
  WHERE id = 600075;