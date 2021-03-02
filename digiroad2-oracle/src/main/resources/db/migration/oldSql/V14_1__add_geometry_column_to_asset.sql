insert into user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
  values ('ASSET', 'GEOMETRY', mdsys.sdo_dim_array(mdsys.sdo_dim_element('X',50100,762000,0.1),
                                                   mdsys.sdo_dim_element('Y',6582000,7800000,0.1)),
          3067
);

alter table asset add geometry sdo_geometry;

