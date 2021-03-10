alter table lrm_position
  add (prod_road_link_id number);

create index prod_road_link_id_idx
  on lrm_position ("PROD_ROAD_LINK_ID");