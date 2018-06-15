create table notification
(
  id number primary key,
  created_date DATE default current_date not null,
  heading varchar2(128),
  content varchar2(4000)
);