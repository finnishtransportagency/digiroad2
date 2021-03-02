create table ely
(
  id number(3) primary key,
  name_fi varchar2(512) not null,
  name_sv varchar2(512) not null,
  geometry sdo_geometry,
  zoom number(2)
);



create table service_area
(
  id number(3) primary key,
  geometry sdo_geometry,
  zoom number(2)
);