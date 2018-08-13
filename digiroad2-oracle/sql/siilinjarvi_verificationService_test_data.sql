INSERT ALL
  -- Data used on UnitTests "get assets Latests Modifications with one municipality" and "get assets Latests Modifications for Ely user with two municipalities"
  INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (primary_key_seq.NEXTVAL, 100, TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'testuser')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (lrm_position_primary_key_seq.NEXTVAL, 1000, null, 0.000, 25.000)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (primary_key_seq.CURRVAL,lrm_position_primary_key_seq.CURRVAL)

SELECT * from dual;

INSERT ALL
  INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (primary_key_seq.NEXTVAL, 30, TO_TIMESTAMP('2016-02-19 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'testuser')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (lrm_position_primary_key_seq.NEXTVAL, 2000, null, 0.000, 25.000)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (primary_key_seq.CURRVAL,lrm_position_primary_key_seq.CURRVAL)

SELECT * from dual;

INSERT ALL
  INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (primary_key_seq.NEXTVAL, 50, TO_TIMESTAMP('2016-02-21 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'testuser')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (lrm_position_primary_key_seq.NEXTVAL, 3000, null, 0.000, 25.000)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (primary_key_seq.CURRVAL,lrm_position_primary_key_seq.CURRVAL)

SELECT * from dual;

INSERT ALL
  INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (primary_key_seq.NEXTVAL, 70, TO_TIMESTAMP('2016-02-21 15:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'testuser')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (lrm_position_primary_key_seq.NEXTVAL, 4000, null, 0.000, 25.000)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (primary_key_seq.CURRVAL,lrm_position_primary_key_seq.CURRVAL)

SELECT * from dual;

INSERT ALL
  INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (primary_key_seq.NEXTVAL, 70, TO_TIMESTAMP('2016-02-21 15:33:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'testuser')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (lrm_position_primary_key_seq.NEXTVAL, 5000, null, 0.000, 25.000)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (primary_key_seq.CURRVAL,lrm_position_primary_key_seq.CURRVAL)

SELECT * from dual;

INSERT ALL
  INTO ASSET (ID,ASSET_TYPE_ID,MODIFIED_DATE, MODIFIED_BY) values (primary_key_seq.NEXTVAL, 90, TO_TIMESTAMP('2016-02-23 15:33:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'testuser')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE) VALUES (lrm_position_primary_key_seq.NEXTVAL, 6000, null, 0.000, 25.000)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (primary_key_seq.CURRVAL,lrm_position_primary_key_seq.CURRVAL)

SELECT * from dual;
