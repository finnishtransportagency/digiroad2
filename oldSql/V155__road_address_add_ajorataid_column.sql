ALTER TABLE ROAD_ADDRESS ADD COMMON_HISTORY_ID NUMBER(38);

CREATE sequence common_history_seq
  increment by 1
  start with 187692978
  minvalue 1
  maxvalue 999999999999999999999999999
  cache 100
  cycle;