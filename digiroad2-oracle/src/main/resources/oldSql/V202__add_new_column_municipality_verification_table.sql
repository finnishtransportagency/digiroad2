alter table municipality_verification add (
	last_user_modification varchar2(128),
	last_date_modification  TIMESTAMP (6),
	number_of_assets integer default 0,
	refresh_date TIMESTAMP(6)
	);

create index municipality_idx on municipality_verification (municipality_id);
create index municipality_assettype_idx on municipality_verification (municipality_id, asset_type_id);

