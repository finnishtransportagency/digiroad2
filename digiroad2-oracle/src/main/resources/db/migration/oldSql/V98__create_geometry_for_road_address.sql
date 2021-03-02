insert into user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
  values ('ROAD_ADDRESS', 'GEOMETRY', mdsys.sdo_dim_array(mdsys.sdo_dim_element('X',50100,762000,0.1),
                                      mdsys.sdo_dim_element('Y',6582000,7800000,0.1),
                                      mdsys.sdo_dim_element('Z',0,2000,0.1),
                                      mdsys.sdo_dim_element('M',0,100000,0.1)),
                                    3067);

alter table road_address add geometry sdo_geometry;

create index road_address_geometry_sx
  on road_address(geometry)
  indextype is mdsys.spatial_index;
