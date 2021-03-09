alter table functional_class drop primary key;
alter table functional_class add constraint pk_functional_class primary key (mml_id);

alter table link_type drop primary key;
alter table link_type add constraint pk_link_type primary key (mml_id);

alter table traffic_direction drop primary key;
alter table traffic_direction add constraint pk_traffic_direction primary key (mml_id);