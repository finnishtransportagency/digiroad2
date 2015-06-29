drop sequence lrm_position_primary_key_seq;
create sequence lrm_position_primary_key_seq
  minvalue 1
  maxvalue 999999999999999999999999999
  start with 50000000
  increment by 1
  cache 100
  cycle;

drop sequence primary_key_seq;
create sequence primary_key_seq
  minvalue 1
  maxvalue 999999999999999999999999999
  start with 400000
  increment by 1
  cache 100
  cycle;
