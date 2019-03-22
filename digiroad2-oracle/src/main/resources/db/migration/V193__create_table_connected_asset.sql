create table connected_asset (
  asset_id number references asset not null,
  connected_asset_id number references asset not null,
 constraint asset_asset UNIQUE (asset_id, connected_asset_id)
);
