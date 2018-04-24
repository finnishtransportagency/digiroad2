create table validity_period_property_value (
  id number primary key,
  asset_id number references asset not null,
  property_id references property not null,
  type integer,
  period_week_day integer not null,
  start_hour integer not null,
  end_hour integer not null,
  start_minute integer default 0 not null,
  end_minute integer default 0 not null,
  constraint minute_constraint check (start_minute between 0 and 59 and end_minute between 0 and 59)
);