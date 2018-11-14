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

insert into ely (id, name_fi, name_sv, geometry, zoom) values (0,'Ahvenanmaa','Aland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1),MDSYS.SDO_ORDINATE_ARRAY(117869.307159741,6695233.48910223, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (1,'Lappi','Lappland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(470092.69926,7504895.42551454, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (2,'Pohjois-Pohjanmaa','Norra Osterbotten', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1),MDSYS.SDO_ORDINATE_ARRAY(502448.375683011,7185083.81896492, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (3,'Etela-Pohjanmaa','Sodra Osterbotten', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(273178.19125461,6999709.7974981, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (4,'Keski-Suomi','Mellersta Finland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1),MDSYS.SDO_ORDINATE_ARRAY(418850.209302328,6932071.78237523, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (5,'Pohjois-Savo','Norra Savolax', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(574999.408689495,6950155.809661, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (6,'Pirkanmaa','Birkaland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(323996.151419635,6844239.172157, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (7,'Kaakkois-Suomi','Sydvastra Finland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(230120.084611288,6748148.87306083, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (8,'Uusimaa','Nyland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1), MDSYS.SDO_ORDINATE_ARRAY(533880.796720066,6756886.74130609, 0, 0)), 3);
insert into ely (id, name_fi, name_sv, geometry, zoom) values (9,'Varsinais-Suomi','Egentliga Finland', MDSYS.SDO_GEOMETRY( 4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1, 1, 1),MDSYS.SDO_ORDINATE_ARRAY(383467.095615344,6718181.18728251, 0, 0)), 3);
