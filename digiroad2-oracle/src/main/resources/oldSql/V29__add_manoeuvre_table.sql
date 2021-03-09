create table manoeuvre
(
  id number(38, 0) not null,
  type number(1, 0) not null,
  road_link_id number(38, 0) not null,
  element_type number(5, 0) not null,
  created_date timestamp default current_timestamp not null,
  created_by varchar2(128)
);

