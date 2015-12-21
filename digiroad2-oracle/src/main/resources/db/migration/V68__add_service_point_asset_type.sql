insert into asset_type (id, name, geometry_type) values(250, 'Palvelupiste', 'point');

create table service_point_value(
  id number primary key,
  asset_id number references asset not null,
  type integer not null,
  additional_info varchar2(4000),
  name varchar2(128),
  type_extension number
);

