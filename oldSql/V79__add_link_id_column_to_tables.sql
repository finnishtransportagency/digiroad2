alter table lrm_position add link_id number(38, 0);
create index lrm_position_link_id_idx on lrm_position(link_id);

alter table traffic_direction add link_id number(38, 0);
alter table incomplete_link add link_id number(38, 0);
alter table functional_class add link_id number(38, 0);
alter table link_type add link_id number(38, 0);
alter table manoeuvre_element add link_id number(38, 0);
alter table unknown_speed_limit add link_id number(38, 0);
