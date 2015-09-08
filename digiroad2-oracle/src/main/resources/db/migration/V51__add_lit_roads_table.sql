create table lit_roads (
  id number primary key,
  mml_id number(10, 0),
  start_measure number(8,3),
  end_measure number(8,3),
  created_date timestamp default current_timestamp not null,
  created_by varchar2(128),
	valid_to timestamp,
	modified_by varchar2(128)
);
