drop table multiple_choice_value;
drop table single_choice_value;
drop table enumerated_value;
drop table text_property_value;
drop table asset;
drop table property;
drop table asset_type;
drop table image;
drop table service_user;
drop table road_node;
drop table lrm_position;
drop table road_link;
drop table ely;
drop table municipality;
drop sequence primary_key_seq;
drop sequence road_node_seq;

delete from mdsys.user_sdo_geom_metadata
  where table_name in ('ROAD_NODE', 'ROAD_LINK');

