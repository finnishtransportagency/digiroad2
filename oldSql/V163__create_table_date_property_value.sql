create table date_property_value (
	id number primary key,
	asset_id number references asset not null,
	property_id references property not null,
	date_time TIMESTAMP not null
);