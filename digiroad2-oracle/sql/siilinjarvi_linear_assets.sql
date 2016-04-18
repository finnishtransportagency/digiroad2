create sequence siilinjarvi_key_seq
  minvalue 1
  maxvalue 699999
  start with 650000
  increment by 1
  cache 100
  cycle;

create sequence siilinjarvi_lrm_key_seq
  minvalue 1
  maxvalue 649999
  start with 620000
  increment by 1
  cache 100
  cycle;

INSERT ALL
-- Lit Road; Cases 5&6 (divided): OLD_ID: 5169516 --> NEW_ID: 6565223, NEW_ID: 6565226
  INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (siilinjarvi_key_seq.NEXTVAL,100,0,'testfixture')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL, 5169516, null, 0.000, 10.551, 1)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (siilinjarvi_key_seq.CURRVAL,siilinjarvi_lrm_key_seq.CURRVAL)
  INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (siilinjarvi_lrm_key_seq.NEXTVAL, siilinjarvi_key_seq.CURRVAL,1,(select id from property where public_id = 'mittarajoitus'))

SELECT * from dual;
INSERT ALL

-- Paved Road; Cases 5&6 (divided into three): OLD_ID: 5169764 --> NEW_IDS: 6565284,  6565286, 6565287
  INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (siilinjarvi_key_seq.NEXTVAL,110,0,'testfixture')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL, 5169764, null, 0.000, 380.551, 1)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (siilinjarvi_key_seq.CURRVAL,siilinjarvi_lrm_key_seq.CURRVAL)
  INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (siilinjarvi_lrm_key_seq.NEXTVAL, siilinjarvi_key_seq.CURRVAL,1,(select id from property where public_id = 'mittarajoitus'))

SELECT * from dual;
INSERT ALL

-- Thawing; Cases 1&2 (3 old links combined):  OLD_ID: 2225999, OLD_ID: 2226035, OLD_ID: 2226036  --> NEW_ID: 6564314

  INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (siilinjarvi_key_seq.NEXTVAL,130,0,'testfixture')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL, 2225999, null, 0.000, 20.551, 1)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (siilinjarvi_key_seq.CURRVAL,siilinjarvi_lrm_key_seq.CURRVAL)
  INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (siilinjarvi_lrm_key_seq.NEXTVAL, siilinjarvi_key_seq.CURRVAL,1,(select id from property where public_id = 'mittarajoitus'))

SELECT * from dual;
INSERT ALL

  INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (siilinjarvi_key_seq.NEXTVAL,130,0,'testfixture')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL, 2226035, null, 0.000, 20.551, 1)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (siilinjarvi_key_seq.CURRVAL,siilinjarvi_lrm_key_seq.CURRVAL)
  INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (siilinjarvi_lrm_key_seq.NEXTVAL, siilinjarvi_key_seq.CURRVAL,1,(select id from property where public_id = 'mittarajoitus'))

SELECT * from dual;
INSERT ALL

  INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (siilinjarvi_key_seq.NEXTVAL,130,0,'testfixture')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL, 2226036, null, 0.000, 20.551, 1)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (siilinjarvi_key_seq.CURRVAL,siilinjarvi_lrm_key_seq.CURRVAL)
  INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (siilinjarvi_lrm_key_seq.NEXTVAL, siilinjarvi_key_seq.CURRVAL,1,(select id from property where public_id = 'mittarajoitus'))

SELECT * from dual;
INSERT ALL

-- Vehicle Prohibition; Cases 1&2 (3 old links combined):  OLD_ID: 2225999, OLD_ID: 2226035, OLD_ID: 2226036  --> NEW_ID: 6564314

  INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (siilinjarvi_key_seq.NEXTVAL,190,0,'testfixture')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL, 2225999, null, 0.000, 20.551, 1)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (siilinjarvi_key_seq.CURRVAL,siilinjarvi_lrm_key_seq.CURRVAL)
  INTO PROHIBITION_VALUE (ID, ASSET_ID, TYPE) VALUES (siilinjarvi_key_seq.NEXTVAL,siilinjarvi_key_seq.CURRVAL,24)
  INTO PROHIBITION_VALIDITY_PERIOD (ID, PROHIBITION_VALUE_ID, TYPE, START_HOUR, END_HOUR) VALUES (siilinjarvi_key_seq.NEXTVAL,siilinjarvi_key_seq.CURRVAL,1,11,12)
  INTO PROHIBITION_EXCEPTION (ID, PROHIBITION_VALUE_ID, TYPE) VALUES (siilinjarvi_key_seq.NEXTVAL, siilinjarvi_key_seq.CURRVAL, 10)

SELECT * from dual;
INSERT ALL

  INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (siilinjarvi_key_seq.NEXTVAL,190,0,'testfixture')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL, 2226035, null, 0.000, 20.551, 1)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (siilinjarvi_key_seq.CURRVAL,siilinjarvi_lrm_key_seq.CURRVAL)
  INTO PROHIBITION_VALUE (ID, ASSET_ID, TYPE) VALUES (siilinjarvi_key_seq.NEXTVAL,siilinjarvi_key_seq.CURRVAL,24)
  INTO PROHIBITION_VALIDITY_PERIOD (ID, PROHIBITION_VALUE_ID, TYPE, START_HOUR, END_HOUR) VALUES (siilinjarvi_key_seq.NEXTVAL,siilinjarvi_key_seq.CURRVAL,1,11,12)
  INTO PROHIBITION_EXCEPTION (ID, PROHIBITION_VALUE_ID, TYPE) VALUES (siilinjarvi_key_seq.NEXTVAL, siilinjarvi_key_seq.CURRVAL, 10)

SELECT * from dual;
INSERT ALL

  INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (siilinjarvi_key_seq.NEXTVAL,190,0,'testfixture')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL, 2226036, null, 0.000, 20.551, 1)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (siilinjarvi_key_seq.CURRVAL,siilinjarvi_lrm_key_seq.CURRVAL)
  INTO PROHIBITION_VALUE (ID, ASSET_ID, TYPE) VALUES (siilinjarvi_key_seq.NEXTVAL,siilinjarvi_key_seq.CURRVAL,24)
  INTO PROHIBITION_VALIDITY_PERIOD (ID, PROHIBITION_VALUE_ID, TYPE, START_HOUR, END_HOUR) VALUES (siilinjarvi_key_seq.NEXTVAL,siilinjarvi_key_seq.CURRVAL,1,11,12)
  INTO PROHIBITION_EXCEPTION (ID, PROHIBITION_VALUE_ID, TYPE) VALUES (siilinjarvi_key_seq.NEXTVAL, siilinjarvi_key_seq.CURRVAL, 10)

SELECT * from dual;
INSERT ALL

-- Mass transport lanes; Cases 3&4 (elongated road) 6470196 -> 6470196

  INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (siilinjarvi_key_seq.NEXTVAL,160,0,'testfixture')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL, 6470196, 321633591, 0.000, 153.57, 1)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (siilinjarvi_key_seq.CURRVAL,siilinjarvi_lrm_key_seq.CURRVAL)
  INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (siilinjarvi_lrm_key_seq.NEXTVAL, siilinjarvi_key_seq.CURRVAL,1,(select id from property where public_id = 'mittarajoitus'))

SELECT * from dual;
INSERT ALL

-- Congestion; Cases 3&4 (lengthened): OLD_ ID: 2226334, NEW_ID: 2226334

  INTO ASSET (ID,ASSET_TYPE_ID,FLOATING) values (siilinjarvi_key_seq.NEXTVAL,150,0)
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL, 2226334, null, 0.000, 426.5, 2)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (siilinjarvi_key_seq.CURRVAL,siilinjarvi_lrm_key_seq.CURRVAL)
  INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (siilinjarvi_lrm_key_seq.NEXTVAL, siilinjarvi_key_seq.CURRVAL,1,(select id from property where public_id = 'mittarajoitus'))

SELECT * from dual;
INSERT ALL

  INTO ASSET (ID,ASSET_TYPE_ID,FLOATING) values (siilinjarvi_key_seq.NEXTVAL,150,0)
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL, 2226334, null, 0.000, 426.5, 3)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (siilinjarvi_key_seq.CURRVAL,siilinjarvi_lrm_key_seq.CURRVAL)
  INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (siilinjarvi_lrm_key_seq.NEXTVAL, siilinjarvi_key_seq.CURRVAL,1,(select id from property where public_id = 'mittarajoitus'))

SELECT * from dual;

-- Cases 7&8 (shortened): OLD_ID: 2226381, NEW_ID: 2226381
-- Put correct asset type ids and values to (nnn,nnnn) when using these!

-- INSERT ALL
--   INTO ASSET (ID,ASSET_TYPE_ID,FLOATING) values (siilinjarvi_key_seq.NEXTVAL,nnn,0)
--   INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL, 2226381, null, 0.000, 80, 1)
--   INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (siilinjarvi_key_seq.CURRVAL,siilinjarvi_lrm_key_seq.CURRVAL)
--   INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (siilinjarvi_lrm_key_seq.NEXTVAL, siilinjarvi_key_seq.CURRVAL,1,(select id from property where public_id = 'mittarajoitus'))
--
-- SELECT * from dual;
-- INSERT ALL
--   INTO ASSET (ID,ASSET_TYPE_ID,FLOATING) values (siilinjarvi_key_seq.NEXTVAL,nnn,0)
--   INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL, 2226381, null, 80, 163.6, 1)
--   INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (siilinjarvi_key_seq.CURRVAL,siilinjarvi_lrm_key_seq.CURRVAL)
--   INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (siilinjarvi_lrm_key_seq.NEXTVAL, siilinjarvi_key_seq.CURRVAL,1,(select id from property where public_id = 'mittarajoitus'))
-- SELECT * from dual;

INSERT ALL
-- Hazmat prohibitions; Cases 5&6 (divided): OLD_ID: 5169516 --> NEW_ID: 6565223, NEW_ID: 6565226
  INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (siilinjarvi_key_seq.NEXTVAL,210,0,'testfixture')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL, 5169516, null, 0.000, 91.316, 2)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (siilinjarvi_key_seq.CURRVAL,siilinjarvi_lrm_key_seq.CURRVAL)
  INTO PROHIBITION_VALUE (ID, ASSET_ID, TYPE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL,siilinjarvi_key_seq.CURRVAL,24)
  INTO PROHIBITION_VALIDITY_PERIOD (ID, PROHIBITION_VALUE_ID, TYPE, START_HOUR, END_HOUR) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL,siilinjarvi_lrm_key_seq.CURRVAL,1,8,12)

SELECT * from dual;
INSERT ALL

  INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (siilinjarvi_key_seq.NEXTVAL,210,0,'testfixture')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL, 5169516, null, 0.000, 91.316, 3)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (siilinjarvi_key_seq.CURRVAL,siilinjarvi_lrm_key_seq.CURRVAL)
  INTO PROHIBITION_VALUE (ID, ASSET_ID, TYPE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL,siilinjarvi_key_seq.CURRVAL,24)
  INTO PROHIBITION_VALIDITY_PERIOD (ID, PROHIBITION_VALUE_ID, TYPE, START_HOUR, END_HOUR) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL,siilinjarvi_lrm_key_seq.CURRVAL,1,8,12)

SELECT * from dual;

INSERT ALL

-- Axle weight limit; Cases 1&2 (3 old links combined):  OLD_ID: 2225999, OLD_ID: 2226035, OLD_ID: 2226036  --> NEW_ID: 6564314

  INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (siilinjarvi_key_seq.NEXTVAL,50,0,'testfixture')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL, 2225999, null, 0.000, 22.612, 1)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (siilinjarvi_key_seq.CURRVAL,siilinjarvi_lrm_key_seq.CURRVAL)
  INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (siilinjarvi_lrm_key_seq.NEXTVAL, siilinjarvi_key_seq.CURRVAL,500,(select id from property where public_id = 'mittarajoitus'))

SELECT * from dual;
INSERT ALL

  INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (siilinjarvi_key_seq.NEXTVAL,50,0,'testfixture')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL, 2226036, null, 0.000, 106.634, 2)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (siilinjarvi_key_seq.CURRVAL,siilinjarvi_lrm_key_seq.CURRVAL)
  INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (siilinjarvi_lrm_key_seq.NEXTVAL, siilinjarvi_key_seq.CURRVAL,500,(select id from property where public_id = 'mittarajoitus'))

SELECT * from dual;

INSERT ALL

  INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (siilinjarvi_key_seq.NEXTVAL,50,0,'testfixture')
  INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (siilinjarvi_lrm_key_seq.NEXTVAL, 2226036, null, 0.000, 106.634, 3)
  INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (siilinjarvi_key_seq.CURRVAL,siilinjarvi_lrm_key_seq.CURRVAL)
  INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (siilinjarvi_lrm_key_seq.NEXTVAL, siilinjarvi_key_seq.CURRVAL,500,(select id from property where public_id = 'mittarajoitus'))

SELECT * from dual;

drop sequence siilinjarvi_key_seq;
drop sequence siilinjarvi_lrm_key_seq;
