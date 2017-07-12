alter session set nls_language = 'american' NLS_NUMERIC_CHARACTERS = ', ';

--Creation of Projects
Insert into PROJECT (ID,STATE,NAME,ELY,CREATED_BY,CREATED_DATE,MODIFIED_BY,MODIFIED_DATE,ADD_INFO,START_DATE,STATUS_INFO,CHECK_COUNTER) values (VIITE_PROJECT_SEQ.nextval,'1','Test01','8','silari',to_date('17.07.12','RR.MM.DD'),'-',to_date('17.07.12','RR.MM.DD'),null,to_date('17.07.12','RR.MM.DD'),null,'0');

--Creation of LRM_Positions
Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE,LINK_SOURCE) values (lrm_position_primary_key_seq.nextval,null,'2','0','492,902',null,'2227748','0',to_timestamp('17.07.12 17:23:32,131636000','RR.MM.DD HH24:MI:SSXFF'),'1');

--Creation of Project_Link's
Insert into PROJECT_LINK (ID,PROJECT_ID,TRACK_CODE,DISCONTINUITY_TYPE,ROAD_NUMBER,ROAD_PART_NUMBER,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,CREATED_BY,MODIFIED_BY,CREATED_DATE,MODIFIED_DATE,STATUS,CALIBRATION_POINTS,ROAD_TYPE) values (viite_general_seq.nextval,VIITE_PROJECT_SEQ.currval,'2','5','5','201','2007','2503',lrm_position_primary_key_seq.currval,'silari',null,to_date('17.07.12','RR.MM.DD'),null,'0','0','99');
