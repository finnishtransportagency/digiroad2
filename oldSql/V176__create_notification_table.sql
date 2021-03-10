create table user_notification
(
  id number primary key,
  created_date DATE default sysdate,
  heading varchar2(256),
  content varchar2(4000)
);


CREATE SEQUENCE user_notification_seq
 START WITH     1
 INCREMENT BY   1
 NOCACHE
 NOCYCLE;