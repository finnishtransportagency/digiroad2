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

-- Lit Road; Cases 5&6 (divided): OLD_ID: 5169516 --> NEW_ID: 6565223, NEW_ID: 6565226
INSERT INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (nextval('siilinjarvi_key_seq'),100,0,'testfixture');
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('siilinjarvi_lrm_key_seq'), 5169516, null, 0.000, 10.551, 1);
INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('siilinjarvi_key_seq'),currval('siilinjarvi_lrm_key_seq'));
INSERT INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (nextval('siilinjarvi_lrm_key_seq'), currval('siilinjarvi_key_seq'),1,(select id from property where public_id = 'mittarajoitus'));
-- Paved Road; Cases 5&6 (dividedINSERT INTO three): OLD_ID: 5169764 --> NEW_IDS: 6565284,  6565286, 6565287
INSERT INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (nextval('siilinjarvi_key_seq'),110,0,'testfixture');
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('siilinjarvi_lrm_key_seq'), 5169764, null, 0.000, 380.551, 1);
INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('siilinjarvi_key_seq'),currval('siilinjarvi_lrm_key_seq'));
INSERT INTO SINGLE_CHOICE_VALUE (ASSET_ID, ENUMERATED_VALUE_ID, PROPERTY_ID, MODIFIED_DATE) VALUES (currval('siilinjarvi_key_seq'), 300274, (select id from property where public_id = 'paallysteluokka'), current_timestamp);
-- Thawing; Cases 1&2 (3 old links combined):  OLD_ID: 2225999, OLD_ID: 2226035, OLD_ID: 2226036  --> NEW_ID: 6564314
INSERT INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (nextval('siilinjarvi_key_seq'),130,0,'testfixture');
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('siilinjarvi_lrm_key_seq'), 2225999, null, 0.000, 20.551, 1);
INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('siilinjarvi_key_seq'),currval('siilinjarvi_lrm_key_seq'));
INSERT INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (nextval('siilinjarvi_lrm_key_seq'), currval('siilinjarvi_key_seq'),1,(select id from property where public_id = 'mittarajoitus'));

INSERT INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (nextval('siilinjarvi_key_seq'),130,0,'testfixture');
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('siilinjarvi_lrm_key_seq'), 2226035, null, 0.000, 20.551, 1);
INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('siilinjarvi_key_seq'),currval('siilinjarvi_lrm_key_seq'));
INSERT INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (nextval('siilinjarvi_lrm_key_seq'), currval('siilinjarvi_key_seq'),1,(select id from property where public_id = 'mittarajoitus'));

INSERT INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (nextval('siilinjarvi_key_seq'),130,0,'testfixture');
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('siilinjarvi_lrm_key_seq'), 2226036, null, 0.000, 20.551, 1);
INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('siilinjarvi_key_seq'),currval('siilinjarvi_lrm_key_seq'));
INSERT INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (nextval('siilinjarvi_lrm_key_seq'), currval('siilinjarvi_key_seq'),1,(select id from property where public_id = 'mittarajoitus'));
-- Vehicle Prohibition; Cases 1&2 (3 old links combined):  OLD_ID: 2225999, OLD_ID: 2226035, OLD_ID: 2226036  --> NEW_ID: 6564314
INSERT INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (nextval('siilinjarvi_key_seq'),190,0,'testfixture');
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('siilinjarvi_lrm_key_seq'), 2225999, null, 0.000, 20.551, 1);
INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('siilinjarvi_key_seq'),currval('siilinjarvi_lrm_key_seq'));
INSERT INTO PROHIBITION_VALUE (ID, ASSET_ID, TYPE) VALUES (nextval('siilinjarvi_key_seq'),currval('siilinjarvi_key_seq'),2);
INSERT INTO PROHIBITION_VALIDITY_PERIOD (ID, PROHIBITION_VALUE_ID, TYPE, START_HOUR, END_HOUR) VALUES (nextval('siilinjarvi_key_seq'),currval('siilinjarvi_key_seq'),1,11,12);
INSERT INTO PROHIBITION_EXCEPTION (ID, PROHIBITION_VALUE_ID, TYPE) VALUES (nextval('siilinjarvi_key_seq'), currval('siilinjarvi_key_seq'), 10);

INSERT INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (nextval('siilinjarvi_key_seq'),190,0,'testfixture');
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('siilinjarvi_lrm_key_seq'), 2226035, null, 0.000, 20.551, 1);
INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('siilinjarvi_key_seq'),currval('siilinjarvi_lrm_key_seq'));
INSERT INTO PROHIBITION_VALUE (ID, ASSET_ID, TYPE) VALUES (nextval('siilinjarvi_key_seq'),currval('siilinjarvi_key_seq'),2);
INSERT INTO PROHIBITION_VALIDITY_PERIOD (ID, PROHIBITION_VALUE_ID, TYPE, START_HOUR, END_HOUR) VALUES (nextval('siilinjarvi_key_seq'),currval('siilinjarvi_key_seq'),1,11,12);
INSERT INTO PROHIBITION_EXCEPTION (ID, PROHIBITION_VALUE_ID, TYPE) VALUES (nextval('siilinjarvi_key_seq'), currval('siilinjarvi_key_seq'), 10);

INSERT INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (nextval('siilinjarvi_key_seq'),190,0,'testfixture');
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('siilinjarvi_lrm_key_seq'), 2226036, null, 0.000, 20.551, 1);
INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('siilinjarvi_key_seq'),currval('siilinjarvi_lrm_key_seq'));
INSERT INTO PROHIBITION_VALUE (ID, ASSET_ID, TYPE) VALUES (nextval('siilinjarvi_key_seq'),currval('siilinjarvi_key_seq'),2);
INSERT INTO PROHIBITION_VALIDITY_PERIOD (ID, PROHIBITION_VALUE_ID, TYPE, START_HOUR, END_HOUR) VALUES (nextval('siilinjarvi_key_seq'),currval('siilinjarvi_key_seq'),1,11,12);
INSERT INTO PROHIBITION_EXCEPTION (ID, PROHIBITION_VALUE_ID, TYPE) VALUES (nextval('siilinjarvi_key_seq'), currval('siilinjarvi_key_seq'), 10);
-- Mass transport lanes; Cases 3&4 (elongated road) 6470196 -> 6470196
INSERT INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (nextval('siilinjarvi_key_seq'),160,0,'testfixture');
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('siilinjarvi_lrm_key_seq'), 6470196, 321633591, 0.000, 153.57, 1);
INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('siilinjarvi_key_seq'),currval('siilinjarvi_lrm_key_seq'));
INSERT INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (nextval('siilinjarvi_lrm_key_seq'), currval('siilinjarvi_key_seq'),1,(select id from property where public_id = 'mittarajoitus'));
-- Congestion; Cases 3&4 (lengthened): OLD_ ID: 2226334, NEW_ID: 2226334
INSERT INTO ASSET (ID,ASSET_TYPE_ID,FLOATING) values (nextval('siilinjarvi_key_seq'),150,0);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('siilinjarvi_lrm_key_seq'), 2226334, null, 0.000, 426.5, 2);
INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('siilinjarvi_key_seq'),currval('siilinjarvi_lrm_key_seq'));
INSERT INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (nextval('siilinjarvi_lrm_key_seq'), currval('siilinjarvi_key_seq'),1,(select id from property where public_id = 'mittarajoitus'));

INSERT INTO ASSET (ID,ASSET_TYPE_ID,FLOATING) values (nextval('siilinjarvi_key_seq'),150,0);
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('siilinjarvi_lrm_key_seq'), 2226334, null, 0.000, 426.5, 3);
INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('siilinjarvi_key_seq'),currval('siilinjarvi_lrm_key_seq'));
INSERT INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (nextval('siilinjarvi_lrm_key_seq'), currval('siilinjarvi_key_seq'),1,(select id from property where public_id = 'mittarajoitus'));

-- Cases 7&8 (shortened): OLD_ID: 2226381, NEW_ID: 2226381
-- Put correct asset type ids and values to (nnn,nnnn) when using these!

--  INSERT INTO ASSET (ID,ASSET_TYPE_ID,FLOATING) values (nextval('siilinjarvi_key_seq'),nnn,0);
--  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('siilinjarvi_lrm_key_seq'), 2226381, null, 0.000, 80, 1);
--  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('siilinjarvi_key_seq'),currval('siilinjarvi_lrm_key_seq'));
--  INSERT INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (nextval('siilinjarvi_lrm_key_seq'), currval('siilinjarvi_key_seq'),1,(select id from property where public_id = 'mittarajoitus'));

--  INSERT INTO ASSET (ID,ASSET_TYPE_ID,FLOATING) values (nextval('siilinjarvi_key_seq'),nnn,0);
--  INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('siilinjarvi_lrm_key_seq'), 2226381, null, 80, 163.6, 1);
--  INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('siilinjarvi_key_seq'),currval('siilinjarvi_lrm_key_seq'));
--  INSERT INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (nextval('siilinjarvi_lrm_key_seq'), currval('siilinjarvi_key_seq'),1,(select id from property where public_id = 'mittarajoitus'));

-- Hazmat prohibitions; Cases 5&6 (divided): OLD_ID: 5169516 --> NEW_ID: 6565223, NEW_ID: 6565226
INSERT INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (nextval('siilinjarvi_key_seq'),210,0,'testfixture');
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('siilinjarvi_lrm_key_seq'), 5169516, null, 0.000, 91.316, 2);
INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('siilinjarvi_key_seq'),currval('siilinjarvi_lrm_key_seq'));
INSERT INTO PROHIBITION_VALUE (ID, ASSET_ID, TYPE) VALUES (nextval('siilinjarvi_lrm_key_seq'),currval('siilinjarvi_key_seq'),24);
INSERT INTO PROHIBITION_VALIDITY_PERIOD (ID, PROHIBITION_VALUE_ID, TYPE, START_HOUR, END_HOUR) VALUES (nextval('siilinjarvi_lrm_key_seq'),currval('siilinjarvi_lrm_key_seq'),1,8,12);

INSERT INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (nextval('siilinjarvi_key_seq'),210,0,'testfixture');
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('siilinjarvi_lrm_key_seq'), 5169516, null, 0.000, 91.316, 3);
INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('siilinjarvi_key_seq'),currval('siilinjarvi_lrm_key_seq'));
INSERT INTO PROHIBITION_VALUE (ID, ASSET_ID, TYPE) VALUES (nextval('siilinjarvi_lrm_key_seq'),currval('siilinjarvi_key_seq'),24);
INSERT INTO PROHIBITION_VALIDITY_PERIOD (ID, PROHIBITION_VALUE_ID, TYPE, START_HOUR, END_HOUR) VALUES (nextval('siilinjarvi_lrm_key_seq'),currval('siilinjarvi_lrm_key_seq'),1,8,12);
-- Axle weight limit; Cases 1&2 (3 old links combined):  OLD_ID: 2225999, OLD_ID: 2226035, OLD_ID: 2226036  --> NEW_ID: 6564314
INSERT INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (nextval('siilinjarvi_key_seq'),50,0,'testfixture');
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('siilinjarvi_lrm_key_seq'), 2225999, null, 0.000, 22.612, 1);
INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('siilinjarvi_key_seq'),currval('siilinjarvi_lrm_key_seq'));
INSERT INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (nextval('siilinjarvi_lrm_key_seq'), currval('siilinjarvi_key_seq'),500,(select id from property where public_id = 'mittarajoitus'));

INSERT INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (nextval('siilinjarvi_key_seq'),50,0,'testfixture');
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('siilinjarvi_lrm_key_seq'), 2226036, null, 0.000, 106.634, 2);
INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('siilinjarvi_key_seq'),currval('siilinjarvi_lrm_key_seq'));
INSERT INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (nextval('siilinjarvi_lrm_key_seq'), currval('siilinjarvi_key_seq'),500,(select id from property where public_id = 'mittarajoitus'));

INSERT INTO ASSET (ID,ASSET_TYPE_ID,FLOATING,CREATED_BY) values (nextval('siilinjarvi_key_seq'),50,0,'testfixture');
INSERT INTO LRM_POSITION (ID, LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (nextval('siilinjarvi_lrm_key_seq'), 2226036, null, 0.000, 106.634, 3);
INSERT INTO ASSET_LINK (ASSET_ID,POSITION_ID) values (currval('siilinjarvi_key_seq'),currval('siilinjarvi_lrm_key_seq'));
INSERT INTO NUMBER_PROPERTY_VALUE (ID, ASSET_ID,"VALUE",PROPERTY_ID) values (nextval('siilinjarvi_lrm_key_seq'), currval('siilinjarvi_key_seq'),500,(select id from property where public_id = 'mittarajoitus'));

drop sequence siilinjarvi_key_seq;
drop sequence siilinjarvi_lrm_key_seq;
