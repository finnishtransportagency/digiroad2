create index road_node_sx
  on road_node(geom)
  indextype is mdsys.spatial_index;

create index road_link_sx
  on road_link(geom)
  indextype is mdsys.spatial_index;

create index text_property_sx
  on text_property_value("ASSET_ID", "PROPERTY_ID");

create index multiple_choice_value_sx
  on multiple_choice_value("ASSET_ID", "PROPERTY_ID");
