create table administrative_class(
  id number primary key,
  mml_id number(10, 0),
  link_id number(38),
  administrative_class number(10, 0),
  vvh_administrative_class number(10, 0),
  modified_date timestamp,
	modified_by varchar2(128)
);
