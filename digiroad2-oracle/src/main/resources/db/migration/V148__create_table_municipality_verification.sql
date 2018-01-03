create table municipality_verification (
  municipality_id references municipality,
  asset_type_id number references asset_type,
  verified_at timestamp,
	verified_by varchar2(128),
);