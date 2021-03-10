drop sequence manoeuvre_id_seq;

create sequence manoeuvre_id_seq
  minvalue 1
  maxvalue 999999999999999999999999999
  start with 200000
  increment by 1
  cache 100
  cycle;
