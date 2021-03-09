drop index road_link_sx;
drop index road_link_municipality_idx;
drop table road_link;
drop table road_node;

delete from mdsys.user_sdo_geom_metadata
  where table_name in ('ROAD_NODE', 'ROAD_LINK');