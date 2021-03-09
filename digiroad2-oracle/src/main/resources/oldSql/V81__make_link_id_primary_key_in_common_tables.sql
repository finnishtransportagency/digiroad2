alter table functional_class drop primary key;
alter table functional_class add id number;
update functional_class set id = primary_key_seq.nextval;
alter table functional_class add primary key (id);
alter table functional_class modify (mml_id null);

alter table link_type drop primary key;
alter table link_type add id number;
update link_type set id = primary_key_seq.nextval;
alter table link_type add primary key (id);
alter table link_type modify (mml_id null);

alter table traffic_direction drop primary key;
alter table traffic_direction add id number;
update traffic_direction set id = primary_key_seq.nextval;
alter table traffic_direction add primary key (id);
alter table traffic_direction modify (mml_id null);

alter table incomplete_link drop primary key;
alter table incomplete_link add id number;
update incomplete_link set id = primary_key_seq.nextval;
alter table incomplete_link add primary key (id);