ALTER TABLE asset RENAME COLUMN external_id TO national_id;
ALTER INDEX external_id_index RENAME TO national_id_index;

ALTER TABLE asset_history RENAME COLUMN external_id TO national_id;
ALTER INDEX hist_external_id_index RENAME TO hist_national_id_index;
