alter table adjusted_traffic_direction
  add (created_date timestamp default current_timestamp not null,
       created_by varchar2(128));

alter table adjusted_functional_class
  add (created_date timestamp default current_timestamp not null,
       created_by varchar2(128));
