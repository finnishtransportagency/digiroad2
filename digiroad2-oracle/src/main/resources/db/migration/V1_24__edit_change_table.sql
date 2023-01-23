ALTER TABLE change_table ALTER COLUMN asset_type_id SET NOT NULL;

ALTER TABLE change_table ALTER COLUMN value_type DROP NOT NULL;
ALTER TABLE change_table ADD CONSTRAINT value_type_not_null_if_value CHECK (value is null or value_type is not null);

ALTER TABLE change_table ALTER COLUMN change_type TYPE int4 USING change_type::integer;
ALTER TABLE change_table ADD CONSTRAINT change_type_values CHECK (change_type in (1,2,3));

ALTER TABLE change_table ADD CONSTRAINT unique_change_event unique (edit_date, edit_by, change_type, asset_type_id, asset_id);
