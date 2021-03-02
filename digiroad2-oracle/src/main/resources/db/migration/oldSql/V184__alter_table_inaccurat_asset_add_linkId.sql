alter table inaccurate_asset add link_id number(38, 0);

alter table inaccurate_asset MODIFY ASSET_ID NUMBER NULL;

alter table inaccurate_asset
add constraint
   assetId_linkId UNIQUE (ASSET_ID, LINK_ID, ASSET_TYPE_ID);