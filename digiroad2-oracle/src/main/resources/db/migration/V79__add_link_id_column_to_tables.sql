alter table lrm_position add link_id number(38, 0);
create index lrm_position_link_id_idx on lrm_position(link_id);

alter table traffic_direction add link_id number(38, 0);
