update user_sdo_geom_metadata
  set diminfo = mdsys.sdo_dim_array(mdsys.sdo_dim_element('X',50100,762000,0.1),
                                    mdsys.sdo_dim_element('Y',6582000,7800000,0.1),
                                    mdsys.sdo_dim_element('Z',0,2000,0.1),
                                    mdsys.sdo_dim_element('M',0,100000,0.1))
  where table_name = 'ASSET' and column_name = 'GEOMETRY';

create index asset_geometry_sx
  on asset(geometry)
  indextype is mdsys.spatial_index;
