insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600070,280,'dr2_test_data',235);
insert into LRM_POSITION (ID,MML_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values (70000020, 388553548, 16.592, 16.592, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600070, 70000020);
UPDATE asset
  SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                    3067,
                                    NULL,
                                    MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                    MDSYS.SDO_ORDINATE_ARRAY(374101.60105163435, 6677437.872017591, 0, 0)
                                   )
  WHERE id = 600070;

insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600071,280,'dr2_test_data',235);
insert into LRM_POSITION (ID,START_MEASURE,END_MEASURE,MML_ID) values (70000021,12.132,12.132,1068804939);
insert into asset_link (ASSET_ID, POSITION_ID) values (600071, 70000021);
UPDATE asset
  SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                    3067,
                                    NULL,
                                    MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                    MDSYS.SDO_ORDINATE_ARRAY(374120.3768218019, 6677432.45742498, 0, 0)
                                   )
  WHERE id = 600071;

-- floating traffic light
insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY,MUNICIPALITY_CODE) values (600072,280,'dr2_test_data',235);
insert into LRM_POSITION (ID,START_MEASURE,END_MEASURE,MML_ID) values (70000022,40,40,388552624);
insert into asset_link (ASSET_ID, POSITION_ID) values (600072, 70000022);
UPDATE asset
  SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                    3067,
                                    NULL,
                                    MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                    MDSYS.SDO_ORDINATE_ARRAY(374102.86204834457,6677412.998813559, 0, 0)
                                   )
  WHERE id = 600072;
