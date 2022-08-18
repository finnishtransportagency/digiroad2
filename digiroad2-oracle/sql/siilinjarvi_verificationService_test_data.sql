  -- Data used on UnitTests "get assets Latests Modifications with one municipality" and "get assets Latests Modifications for Ely user with two municipalities"
  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (nextval('primary_key_seq'), 100,current_timestamp-INTERVAL'3 days','testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, VVH_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 'da2a39b3-82fc-48f1-a1f9-0e63ca09d454:1', 1000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('primary_key_seq'),currval('lrm_position_primary_key_seq'));;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (nextval('primary_key_seq'), 30, current_timestamp-INTERVAL'2 days','testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, VVH_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), '703cdc6d-61f4-4e40-9da8-0efc51e9943d:2', 2000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('primary_key_seq'),currval('lrm_position_primary_key_seq'));;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (nextval('primary_key_seq'), 50, (current_timestamp-INTERVAL'1 days') - interval'4 hour','testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, VVH_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), '76e9fdef-d743-416d-b1d8-1529e98cbb21:3', 3000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('primary_key_seq'),currval('lrm_position_primary_key_seq'));;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (nextval('primary_key_seq'), 70, (current_timestamp-INTERVAL'1 days') - interval'2 hour','testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, VVH_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 'c9f4c80d-a0c8-4ede-977b-9b048cd7099c:4', 4000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('primary_key_seq'),currval('lrm_position_primary_key_seq'));;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (nextval('primary_key_seq'), 70, current_timestamp-INTERVAL'1 days','testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, VVH_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 'ae2ffef1-353d-4c82-8246-108bb89809e1:5', 5000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('primary_key_seq'),currval('lrm_position_primary_key_seq'));;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (nextval('primary_key_seq'), 90, CURRENT_TIMESTAMP,'testuser');
  INSERT INTO LRM_POSITION (ID, LINK_ID, VVH_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), '4e339ea7-0d1c-4b83-ac34-d8cfeebe4066:6', 6000, null, 0.000, 25.000);
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('primary_key_seq'),currval('lrm_position_primary_key_seq'));