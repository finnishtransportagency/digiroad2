insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600029,200,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (70000003, 1611317, 388553074, 103.000, 103.000, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600029, 70000003);
UPDATE asset
  SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                    3067,
                                    NULL,
                                    MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                    MDSYS.SDO_ORDINATE_ARRAY(374467, 6677347, 0, 0)
                                   )
  WHERE id = 600029;


insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600030,200,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (70000004, 1611341, 388553080, 69.000, 69.000, null);
insert into asset_link (ASSET_ID, POSITION_ID) values (600030, 70000004);
UPDATE asset
  SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                    3067,
                                    NULL,
                                    MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                    MDSYS.SDO_ORDINATE_ARRAY(374443.764141219, 6677245.28337185, 0, 0)
                                   )
  WHERE id = 600030;


insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600031,200,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (70000005, 1611353, 1140018963, 109.000, 109.000, null);
insert into asset_link (ASSET_ID, POSITION_ID) values (600031, 70000005);
UPDATE asset
  SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                    3067,
                                    NULL,
                                    MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                    MDSYS.SDO_ORDINATE_ARRAY(374675.043988335,6677274.14596169, 0, 0)
                                   )
  WHERE id = 600031;


insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600032,200,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (70000006, 6488445, 388554364, 113.000, 113.000, null);
insert into asset_link (ASSET_ID, POSITION_ID) values (600032, 70000006);
UPDATE asset
  SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                    3067,
                                    NULL,
                                    MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                    MDSYS.SDO_ORDINATE_ARRAY(374780.259160265, 6677546.84962279 , 0, 0)
                                   )
  WHERE id = 600032;

insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600033,200,'dr2_test_data',235);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (70000007, null, 12345, 100.000, 100.000, null);
insert into asset_link (ASSET_ID, POSITION_ID) values (600033, 70000007);
UPDATE asset
  SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                    3067,
                                    NULL,
                                    MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                    MDSYS.SDO_ORDINATE_ARRAY(374396, 6677319 , 0, 0)
                                   )
  WHERE id = 600033;
