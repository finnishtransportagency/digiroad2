alter table inaccurate_asset add link_id number(38, 0);

alter table inaccurate_asset MODIFY ASSET_ID NUMBER NULL;

alter table inaccurate_asset
add constraint
   asset_info UNIQUE (ASSET_ID, ASSET_TYPE_ID, MUNICIPALITY_CODE, ADMINISTRATIVE_CLASS);

alter table inaccurate_asset
add constraint
   link_info UNIQUE (LINK_ID, ASSET_TYPE_ID, MUNICIPALITY_CODE, ADMINISTRATIVE_CLASS);