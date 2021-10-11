  -- Data used on UnitTests "get assets Latests Modifications with one municipality" and "get assets Latests Modifications for Ely user with two municipalities"
  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (nextval('primary_key_seq'), 100,current_timestamp-INTERVAL'3 days','testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 1000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('primary_key_seq'),currval('lrm_position_primary_key_seq'));;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (nextval('primary_key_seq'), 30, current_timestamp-INTERVAL'2 days','testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 2000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('primary_key_seq'),currval('lrm_position_primary_key_seq'));;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (nextval('primary_key_seq'), 50, (current_timestamp-INTERVAL'1 days') - interval'4 hour','testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 3000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('primary_key_seq'),currval('lrm_position_primary_key_seq'));;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (nextval('primary_key_seq'), 70, (current_timestamp-INTERVAL'1 days') - interval'2 hour','testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 4000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('primary_key_seq'),currval('lrm_position_primary_key_seq'));;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (nextval('primary_key_seq'), 70, current_timestamp-INTERVAL'1 days','testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 5000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('primary_key_seq'),currval('lrm_position_primary_key_seq'));;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (nextval('primary_key_seq'), 90, CURRENT_TIMESTAMP,'testuser');
  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 6000, null, 0.000, 25.000);
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('primary_key_seq'),currval('lrm_position_primary_key_seq'));