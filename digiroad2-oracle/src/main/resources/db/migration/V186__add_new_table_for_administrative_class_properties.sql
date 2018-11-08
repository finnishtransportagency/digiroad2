create table road_link_attributes(
  id number,
  link_id number(38, 0),
  name varchar2(128),
  value varchar2(128),
  created_date date default sysdate not null enable,
  created_by varchar2(128),
  modified_date timestamp (6),
  modified_by varchar2(128),
  valid_to timestamp (6),
  primary key (id)
)