create table ely
(
  id number(3) primary key,
  name_fi varchar2(512) not null,
  name_sv varchar2(512) not null,
  geometry sdo_geometry
);
