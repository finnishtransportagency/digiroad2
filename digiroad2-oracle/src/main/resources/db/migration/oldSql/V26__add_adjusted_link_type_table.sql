create table adjusted_link_type
(
  mml_id number(10) not null,
  link_type number(9) not null,
  modified_date timestamp default current_timestamp not null,
  modified_by varchar2(128)
);
