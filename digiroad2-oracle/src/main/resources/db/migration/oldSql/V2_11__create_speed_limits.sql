CREATE TABLE asset_link (
  ASSET_ID references asset,
	POSITION_ID references lrm_position
);

insert into asset_type (id, name, geometry_type) values(20, 'Nopeusrajoitukset', 'linear');