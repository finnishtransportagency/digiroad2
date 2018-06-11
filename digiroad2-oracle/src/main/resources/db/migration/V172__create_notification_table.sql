create table notification
(
  id number primary key,
  created_date timestamp default current_timestamp not null,
  heading varchar2(128),
  content varchar2(4000)
);