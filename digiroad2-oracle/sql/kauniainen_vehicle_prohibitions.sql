insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY) values (600018,190,'dr2_test_data');
insert into LRM_POSITION (ID,MML_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values (70000000, 388553074, 0, 100, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600018, 70000000);
insert into prohibition_value(ID, ASSET_ID, TYPE) values (600019, 600018, 1);

