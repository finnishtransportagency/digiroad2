  -- Data used on UnitTests "get assets Latests Modifications with one municipality" and "get assets Latests Modifications for Ely user with two municipalities"
  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (primary_key_seq.NEXTVAL, 100, CURRENT_TIMESTAMP - 3,'testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (lrm_position_primary_key_seq.NEXTVAL, 1000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (primary_key_seq.CURRVAL,lrm_position_primary_key_seq.CURRVAL);;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (primary_key_seq.NEXTVAL, 30, CURRENT_TIMESTAMP - 2,'testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (lrm_position_primary_key_seq.NEXTVAL, 2000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (primary_key_seq.CURRVAL,lrm_position_primary_key_seq.CURRVAL);;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (primary_key_seq.NEXTVAL, 50, (CURRENT_TIMESTAMP - 1) - interval '4' hour,'testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (lrm_position_primary_key_seq.NEXTVAL, 3000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (primary_key_seq.CURRVAL,lrm_position_primary_key_seq.CURRVAL);;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (primary_key_seq.NEXTVAL, 70, (CURRENT_TIMESTAMP - 1) - interval '2' hour,'testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (lrm_position_primary_key_seq.NEXTVAL, 4000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (primary_key_seq.CURRVAL,lrm_position_primary_key_seq.CURRVAL);;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (primary_key_seq.NEXTVAL, 70, CURRENT_TIMESTAMP - 1,'testuser');;
  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (lrm_position_primary_key_seq.NEXTVAL, 5000, null, 0.000, 25.000);;
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (primary_key_seq.CURRVAL,lrm_position_primary_key_seq.CURRVAL);;

  INSERT INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (primary_key_seq.NEXTVAL, 90, CURRENT_TIMESTAMP,'testuser');
  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (lrm_position_primary_key_seq.NEXTVAL, 6000, null, 0.000, 25.000);
  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (primary_key_seq.CURRVAL,lrm_position_primary_key_seq.CURRVAL);