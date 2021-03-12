  -- Data used on UnitTests "get assets Latests Modifications with one municipality" and "get assets Latests Modifications for Ely user with two municipalities"
  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (nextval('primary_key_seq'), 100, CURRENT_TIMESTAMP - 3,'testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 1000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (primary_key_seq.CURRVAL,lrm_position_primary_key_seq.CURRVAL);;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (nextval('primary_key_seq'), 30, CURRENT_TIMESTAMP - 2,'testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 2000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (primary_key_seq.CURRVAL,lrm_position_primary_key_seq.CURRVAL);;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (nextval('primary_key_seq'), 50, (CURRENT_TIMESTAMP - 1) - interval '4' hour,'testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 3000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (primary_key_seq.CURRVAL,lrm_position_primary_key_seq.CURRVAL);;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (nextval('primary_key_seq'), 70, (CURRENT_TIMESTAMP - 1) - interval '2' hour,'testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 4000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (primary_key_seq.CURRVAL,lrm_position_primary_key_seq.CURRVAL);;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (nextval('primary_key_seq'), 70, CURRENT_TIMESTAMP - 1,'testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 5000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (primary_key_seq.CURRVAL,lrm_position_primary_key_seq.CURRVAL);;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (nextval('primary_key_seq'), 90, CURRENT_TIMESTAMP,'testuser');
  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (nextval('LRM_POSITION_PRIMARY_KEY_SEQ'), 6000, null, 0.000, 25.000);
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (primary_key_seq.CURRVAL,lrm_position_primary_key_seq.CURRVAL);