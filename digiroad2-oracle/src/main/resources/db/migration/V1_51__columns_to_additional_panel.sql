ALTER TABLE additional_panel ADD COLUMN external_ids VARCHAR(128);
ALTER TABLE additional_panel ADD CONSTRAINT unique_external_ids UNIQUE (external_ids);

ALTER TABLE additional_panel ADD COLUMN created_date timestamp;
ALTER TABLE additional_panel ADD COLUMN modified_date timestamp;