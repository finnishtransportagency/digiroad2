create table feedback
(
  id number primary key,
  receiver varchar2(128),
  createdBy varchar2(128),
  createdAt timestamp default current_timestamp not null,
  subject varchar2(128),
  body varchar2(4000),
  status char(1) default '0',
  statusDate timestamp default current_timestamp,
  CONSTRAINT status check (status in ('1','0'))
);