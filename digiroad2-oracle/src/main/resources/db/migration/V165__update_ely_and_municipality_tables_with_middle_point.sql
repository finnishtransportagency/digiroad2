insert into user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
values ('ely', 'GEOMETRY', mdsys.sdo_dim_array(mdsys.sdo_dim_element('X',50100,762000,0.1),
                                               mdsys.sdo_dim_element('Y',6582000,7800000,0.1)), 3067
);

insert into user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
values ('municipality', 'GEOMETRY', mdsys.sdo_dim_array(mdsys.sdo_dim_element('X',50100,762000,0.1),
                                               mdsys.sdo_dim_element('Y',6582000,7800000,0.1)), 3067
);

insert into user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
values ('service_area', 'GEOMETRY', mdsys.sdo_dim_array(mdsys.sdo_dim_element('X',50100,762000,0.1),
                                               mdsys.sdo_dim_element('Y',6582000,7800000,0.1)), 3067
);

alter table municipality add geometry sdo_geometry;
alter table municipality add zoom number(2);

insert into service_area (id, geometry, zoom) values (1, MDSYS.SDO_GEOMETRY(4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(400157.966173197,6711259.74919401, 0, 0)), 3) ;
insert into service_area (id, geometry, zoom) values (2, MDSYS.SDO_GEOMETRY(4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(290215.504082148,6720355.44251837, 0, 0)), 3) ;
insert into service_area (id, geometry, zoom) values (7, MDSYS.SDO_GEOMETRY(4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(629332.73888689,6899272.62781295, 0, 0)), 3) ;
insert into service_area (id, geometry, zoom) values (8, MDSYS.SDO_GEOMETRY(4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(555439.834031151,6949946.9225817, 0, 0)), 3) ;
insert into service_area (id, geometry, zoom) values (11, MDSYS.SDO_GEOMETRY(4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(552823.569688365,7170442.70659199, 0, 0)), 3) ;
insert into service_area (id, geometry, zoom) values (10, MDSYS.SDO_GEOMETRY(4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(432891.452412774,7030310.13751559, 0, 0)), 3) ;
insert into service_area (id, geometry, zoom) values (6, MDSYS.SDO_GEOMETRY(4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(480712.534783056,6789171.62558283, 0, 0)), 3) ;
insert into service_area (id, geometry, zoom) values (12, MDSYS.SDO_GEOMETRY(4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(474371.454407474,7489709.31159948, 0, 0)), 3) ;
insert into service_area (id, geometry, zoom) values (9, MDSYS.SDO_GEOMETRY(4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(374182.873983145,7123572.41439203, 0, 0)), 3) ;
insert into service_area (id, geometry, zoom) values (3, MDSYS.SDO_GEOMETRY(4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(322958.741249055,6911965.22619885, 0, 0)), 3) ;
insert into service_area (id, geometry, zoom) values (4, MDSYS.SDO_GEOMETRY(4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(351797.769363076,6837369.90653321, 0, 0)), 3) ;
insert into service_area (id, geometry, zoom) values (5, MDSYS.SDO_GEOMETRY(4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(305045.774498648,6933649.48402544, 0, 0)), 3) ;

insert into ely (id, name_fi, name_sv, geometry, zoom) values (1,'Uusimaa','Nyland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(376234.111897691,6694507.48332496, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (2,'Varsinais-Suomi','Egentliga Finland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1),MDSYS.SDO_ORDINATE_ARRAY(257030.353845512,6722374.5377455, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (3,'Satakunta','Satakunta', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(242111.736070352,6830901.01204563, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (4,'Hame','Tavastland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(394316.283098378,6772451.02715757, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (5,'Pirkanmaa','Birkaland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(324070.210770637,6844220.51753451, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (6,'Kaakkois-Suomi','Sydvastra Finland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(537967.23910328,6766853.28611996, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (7,'Etela-Savo','Sodra Savolax', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1),MDSYS.SDO_ORDINATE_ARRAY(544516.210965196,6861753.90125069, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (8,'Pohjois-Savo','Norra Savolax', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(524462.834158647,7002492.12777011, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (9,'Pohjois-Karjala','Norra Karelen', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1),MDSYS.SDO_ORDINATE_ARRAY(649670.020429631,6979115.16585013, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (10,'Keski-Suomi','Mellersta Finland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1),MDSYS.SDO_ORDINATE_ARRAY(418877.176213178,6932160.63849169, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (11,'Etela-Pohjanmaa','Sodra Osterbotten', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(298107.217169727,6962284.45692741, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (12,'Pohjanmaa','Osterbotten', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(295259.570235704,7024548.78774578, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (13,'Pohjois-Pohjanmaa','Norra Osterbotten', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1),MDSYS.SDO_ORDINATE_ARRAY(479229.982118729,7201036.46152363, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (14,'Kainuu','Kajanaland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(582302.049312239,7155707.38654936, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (15,'Lappi','Lappland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(471335.846629911,7508246.0969253, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (16,'Ahvenanmaa','Aland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1),MDSYS.SDO_ORDINATE_ARRAY(117869.307159741,6695233.48910223, 0, 0)), 3);
