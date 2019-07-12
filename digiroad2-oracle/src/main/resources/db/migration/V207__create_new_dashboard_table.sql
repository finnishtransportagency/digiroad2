create table dashboard_info (
	municipality_id number(22),
	asset_type_id number(22),
	modified_by varchar2(128),
	last_modified_date timestamp,
	foreign key (municipality_id) references municipality(id)
);