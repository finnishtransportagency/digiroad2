create table feedback
(
  id number primary key,
  receiver varchar2(128),
  created_by varchar2(128),
  created_date timestamp default current_timestamp not null,
  subject varchar2(128),
  body varchar2(4000),
  status char(1) default '0',
  status_date timestamp default current_timestamp,
  CONSTRAINT status check (status in ('1','0'))
);