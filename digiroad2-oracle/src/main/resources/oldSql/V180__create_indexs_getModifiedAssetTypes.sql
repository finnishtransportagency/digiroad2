 CREATE INDEX asset_type_and_modified_by_idx
  ON ASSET (asset_type_id, modified_by);

 CREATE INDEX type_modified_by_and_date_idx
  ON ASSET (modified_date, asset_type_id, modified_by);