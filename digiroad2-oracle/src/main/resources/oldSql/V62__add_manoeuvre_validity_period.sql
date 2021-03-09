create table manoeuvre_validity_period(
  id number primary key,
  manoeuvre_id number references manoeuvre not null,
  type integer not null,
  start_hour integer not null,
  end_hour integer not null,
  constraint mvp_type_constraint check (type between 1 and 3),
  constraint mvp_hour_constraint check (start_hour between 0 and 24 and end_hour between 0 and 24)
);