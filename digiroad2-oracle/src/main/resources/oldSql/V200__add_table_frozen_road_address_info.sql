create table temp_road_address_info (
 id number,
 link_id NUMBER NOT NULL,
 MUNICIPALITY_code NUMBER NOT NULL,
 ROAD_NUMBER NUMBER NOT NULL,
 ROAD_PART NUMBER NOT NULL,
 TRACK_CODE NUMBER NOT NULL,
 START_ADDRESS_M NUMBER NOT NULL,
 END_ADDRESS_M NUMBER NOT NULL,
 start_m_value NUMBER NOT NULL,
 end_m_value NUMBER NOT NULL,
 side_code numeric default null,
 created_date date default sysdate not null enable,
 created_by varchar2(128),
 primary key (id)
);

create index link_id_temp_address on temp_road_address_info(link_id);
create index municipality_temp_address on temp_road_address_info(MUNICIPALITY_code);
