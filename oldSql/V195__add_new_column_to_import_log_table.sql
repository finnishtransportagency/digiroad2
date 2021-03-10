alter table import_log add (
  file_name varchar2(128),
  status  NUMBER(3) default 1 not null,
  created_date date default sysdate not null enable,
  created_by varchar2(128)
);

create index created_by_idx on import_log (created_by);