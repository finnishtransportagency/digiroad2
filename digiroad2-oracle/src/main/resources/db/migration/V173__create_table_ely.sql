insert into user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
values ('elys', 'GEOMETRY', mdsys.sdo_dim_array(mdsys.sdo_dim_element('X',50100,762000,0.1),
                                               mdsys.sdo_dim_element('Y',6582000,7800000,0.1)), 3067
);


create table elys
(
  id number(3) primary key,
  name_fi varchar2(512) not null,
  name_sv varchar2(512) not null,
  geometry sdo_geometry
);


insert into elys (id, name_fi, name_sv, geometry) values (1,'Uusimaa','Nyland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(376234.111897691,6694507.48332496, 0, 0)));
insert into elys (id, name_fi, name_sv, geometry) values (2,'Varsinais-Suomi','Egentliga Finland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1),MDSYS.SDO_ORDINATE_ARRAY(257030.353845512,6722374.5377455, 0, 0)));
insert into elys (id, name_fi, name_sv, geometry) values (3,'Satakunta','Satakunta', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(242111.736070352,6830901.01204563, 0, 0)));
insert into elys (id, name_fi, name_sv, geometry) values (4,'Hame','Tavastland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(394316.283098378,6772451.02715757, 0, 0)));
insert into elys (id, name_fi, name_sv, geometry) values (5,'Pirkanmaa','Birkaland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(324070.210770637,6844220.51753451, 0, 0)));
insert into elys (id, name_fi, name_sv, geometry) values (6,'Kaakkois-Suomi','Sydvastra Finland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(537967.23910328,6766853.28611996, 0, 0)));
insert into elys (id, name_fi, name_sv, geometry) values (7,'Etela-Savo','Sodra Savolax', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1),MDSYS.SDO_ORDINATE_ARRAY(544516.210965196,6861753.90125069, 0, 0)));
insert into elys (id, name_fi, name_sv, geometry) values (8,'Pohjois-Savo','Norra Savolax', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(524462.834158647,7002492.12777011, 0, 0)));
insert into elys (id, name_fi, name_sv, geometry) values (9,'Pohjois-Karjala','Norra Karelen', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1),MDSYS.SDO_ORDINATE_ARRAY(649670.020429631,6979115.16585013, 0, 0)));
insert into elys (id, name_fi, name_sv, geometry) values (10,'Keski-Suomi','Mellersta Finland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1),MDSYS.SDO_ORDINATE_ARRAY(418877.176213178,6932160.63849169, 0, 0)));
insert into elys (id, name_fi, name_sv, geometry) values (11,'Etela-Pohjanmaa','Sodra Osterbotten', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(298107.217169727,6962284.45692741, 0, 0)));
insert into elys (id, name_fi, name_sv, geometry) values (12,'Pohjanmaa','Osterbotten', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(295259.570235704,7024548.78774578, 0, 0)));
insert into elys (id, name_fi, name_sv, geometry) values (13,'Pohjois-Pohjanmaa','Norra Osterbotten', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1),MDSYS.SDO_ORDINATE_ARRAY(479229.982118729,7201036.46152363, 0, 0)));
insert into elys (id, name_fi, name_sv, geometry) values (14,'Kainuu','Kajanaland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(582302.049312239,7155707.38654936, 0, 0)));
insert into elys (id, name_fi, name_sv, geometry) values (15,'Lappi','Lappland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(471335.846629911,7508246.0969253, 0, 0)));
insert into elys (id, name_fi, name_sv, geometry) values (16,'Ahvenanmaa','Aland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1),MDSYS.SDO_ORDINATE_ARRAY(117869.307159741,6695233.48910223, 0, 0)));

