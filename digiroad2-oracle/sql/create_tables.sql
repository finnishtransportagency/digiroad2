insert into user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
  values ('ROAD_NODE', 'GEOM', mdsys.sdo_dim_array(mdsys.sdo_dim_element('X',50100,762000,0.1),
                                                   mdsys.sdo_dim_element('Y',6582000,7800000,0.1), 
                                                   mdsys.sdo_dim_element('Z',0,100000,0.1)),
          3067
);

insert into user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
  values ('ROAD_LINK', 'GEOM', mdsys.sdo_dim_array(mdsys.sdo_dim_element('X',50100,762000,0.1),
                                                   mdsys.sdo_dim_element('Y',6582000,7800000,0.1),
                                                   mdsys.sdo_dim_element('Z',0,2000,0.1),
                                                   mdsys.sdo_dim_element('M',0,100000,0.1)),
          3067
);

create table road_node
(
  id number(38),
  node_type number(5) not null,
  geom sdo_geometry,
  constraint road_node_pk primary key(id)
);

create index road_node_sx
  on road_node(geom)
  indextype is mdsys.spatial_index;

-- road_link cannot yet have foreign key reference to road_node (no data)

create table road_link
(
  id number(38) not null,
  road_type number(5) not null,
  road_number number(5),
  road_part_number number(5),
  functional_class number(5),
  r_start_hn number(5),
  l_start_hn number(5),
  r_end_hn number(5),
  l_end_hn number(5),
  start_road_node_id number(38),
  end_road_node_id number(38),
  measured_length number(12,3),
  municipality_number number(5),
  end_date date,
  geom sdo_geometry,
  constraint road_link_pk primary key (id)
/*
  ,constraint fk_startnode foreign key (start_road_node_id) references road_node(id),
  constraint fk_startnode foreign key (end_road_node_id) references road_node(id)
*/
);

create index road_link_sx
  on road_link(geom)
  indextype is mdsys.spatial_index;

create table lrm_position
(
  id number(38),
  road_link_id number(38),
  event_type number(5),
  lane_code number(5),
  side_code number(5),
  start_measure number(8,3),
  end_measure number(8,3),
  constraint lrm_position_pk primary key(id),
  constraint fk_road_link foreign key (road_link_id) references road_link(id)
);

create table image (
  id number primary key,
  file_name varchar2(256),
  image_data blob,
  created_by varchar2(128),
  created_date timestamp default current_timestamp not null,
  modified_date timestamp default current_timestamp not null,
  modified_by varchar2(128)
);

create table asset_type (
	id number primary key,
	name varchar2(512) not null,
	geometry_type varchar2(128) not null,
  image_id number references image,
	created_date timestamp default current_timestamp not null,
	created_by varchar2(128),
	modified_date timestamp,
	modified_by varchar2(128)
);

create table asset (
	id number primary key,
	asset_type_id number references asset_type,
	lrm_position_id number,
	created_date timestamp default current_timestamp not null,
	created_by varchar2(128),
	modified_date timestamp,
	modified_by varchar2(128),
	bearing number,
	validity_direction number default 2,
	valid_from timestamp,
	valid_to timestamp,
	constraint validity_period check (valid_from <= valid_to)
);

create table property (
	id number primary key,
	asset_type_id number references asset_type not null,
	name_fi varchar2(1024) not null,
	name_sv varchar2(1024) not null,
	property_type varchar2(128),
  required char default '0' check (required in ('1','0')),
	created_date timestamp default current_timestamp not null,
	created_by varchar2(128),
	modified_date timestamp,
	modified_by varchar2(128)
);

create table text_property_value (
	id number primary key,
	asset_id number references asset not null,
	property_id references property not null,
	value_fi varchar2(4000),
  value_sv varchar2(4000),
	created_date timestamp default current_timestamp not null,
	created_by varchar2(128),
	modified_date timestamp,
	modified_by varchar2(128)
);

create table enumerated_value (
	id number primary key,
	property_id references property not null,
	value number not null,
	name_fi varchar2(512) not null,
	name_sv varchar2(512) not null,
  image_id number references image,
	created_date timestamp default current_timestamp not null,
	created_by varchar2(128),
	modified_date timestamp,
	modified_by varchar2(128)
);

create table single_choice_value (
	asset_id number references asset,
	enumerated_value_id number references enumerated_value not null,
	property_id number references property not null,
	modified_date timestamp,
	modified_by varchar2(128),
	constraint single_choice_pk primary key (asset_id, enumerated_value_id)
);

create table multiple_choice_value (
	id number primary key,
	property_id number references property not null,
	enumerated_value_id number references enumerated_value not null,
	asset_id number references asset not null,
	modified_date timestamp,
	modified_by varchar2(128)
);

create table service_user (
	id number primary key,
	username varchar2(256) unique not null,
	configuration varchar2(4000)
);


create sequence primary_key_seq
  minvalue 1
  maxvalue 999999999999999999999999999
  start with 300000
  increment by 1
  cache 100
  cycle;

create sequence road_node_seq
  minvalue 1
  maxvalue 999999999999999999999999999
  start with 100
  increment by 1
  cache 100
  cycle;
