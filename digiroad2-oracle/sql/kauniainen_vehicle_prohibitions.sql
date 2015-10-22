insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY) values (600018,190,'dr2_test_data');
insert into LRM_POSITION (ID,MML_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values (70000000, 388553074, 0, 100, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600018, 70000000);
insert into prohibition_value(ID, ASSET_ID, TYPE) values (600019, 600018, 1);
insert into PROHIBITION_EXCEPTION(ID, PROHIBITION_VALUE_ID, TYPE) values (600023, 600019, 10);

insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY) values (600020,190,'dr2_test_data');
insert into LRM_POSITION (ID,MML_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values (70000001, 1621077551, 0, 100, 1);
insert into asset_link (ASSET_ID, POSITION_ID) values (600020, 70000001);
insert into prohibition_value(ID, ASSET_ID, TYPE) values (600021, 600020, 1);
insert into prohibition_validity_period(id, prohibition_value_id, type, START_HOUR, END_HOUR) values (600022, 600021, 1, 12, 16);

insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY) values (600024,190,'dr2_test_data');
insert into LRM_POSITION (ID,MML_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values (70000002, 388551874, 0, 100, 1);
insert into ASSET_LINK (ASSET_ID, POSITION_ID) values (600024, 70000002);
insert into PROHIBITION_VALUE (ID, ASSET_ID, TYPE) values (600025, 600024, 1);
insert into PROHIBITION_VALIDITY_PERIOD (id, prohibition_value_id, type, START_HOUR, END_HOUR) values (600026, 600025, 1, 12, 16);
insert into PROHIBITION_EXCEPTION (ID, PROHIBITION_VALUE_ID, TYPE) values (600027, 600025, 1);
insert into PROHIBITION_EXCEPTION (ID, PROHIBITION_VALUE_ID, TYPE) values (600028, 600025, 2);