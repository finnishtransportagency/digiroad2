create table localized_string
(
  id number(38) primary key,
	value_fi varchar2(256),
	value_sv varchar2(256),
  created_date timestamp default current_timestamp not null,
  created_by varchar2(128),
  modified_date timestamp default current_timestamp not null,
  modified_by varchar2(128)
);
alter table enumerated_value add localized_string_id number(38);
alter table text_property_value add localized_string_id number(38);
