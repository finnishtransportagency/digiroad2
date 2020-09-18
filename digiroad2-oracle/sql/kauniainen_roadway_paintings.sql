insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE, CREATED_DATE) values (600081,470,'dr2_test_data',235, add_months(sysdate, -2));
INSERT INTO LRM_POSITION (ID, LINK_ID, START_MEASURE, END_MEASURE, MML_ID) VALUES (70000037, 1611317, 103.000, 103.000, 388553074);
insert into asset_link (ASSET_ID, POSITION_ID) values (600081, 70000037);
insert into single_choice_value(asset_id, enumerated_value_id, property_id) values (600081, (select id from enumerated_value where name_fi='L1 pysäytysviiva'), (select id from property where public_id='widthOfRoadAxisMarking_regulation_number'));
UPDATE asset
  SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                    3067,
                                    NULL,
                                    MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                    MDSYS.SDO_ORDINATE_ARRAY(374467, 6677347, 0, 0)
                                   )
  WHERE id = 600081;
