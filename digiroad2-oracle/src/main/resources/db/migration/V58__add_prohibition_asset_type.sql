insert into asset_type (id, name, geometry_type) values(190, 'Ajokielto', 'linear');

create table prohibition_value(
  id number primary key,
	asset_id number references asset not null,
	type integer not null
);

create table prohibition_exception(
  id number primary key,
  prohibition_value_id number references prohibition_value not null,
  type integer not null
);

create table prohibition_validity_period(
  id number primary key,
  prohibition_value_id number references prohibition_value not null,
  type integer not null,
  start_hour integer not null,
  end_hour integer not null,
  constraint hour_constraint check (start_hour between 0 and 24 and end_hour between 0 and 24)
);