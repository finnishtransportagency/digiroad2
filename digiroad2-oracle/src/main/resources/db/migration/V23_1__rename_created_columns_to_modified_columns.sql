alter table adjusted_traffic_direction
  rename column created_date to modified_date;

alter table adjusted_traffic_direction
  rename column created_by to modified_by;

alter table adjusted_functional_class
  rename column created_date to modified_date;

alter table adjusted_functional_class
  rename column created_by to modified_by;
