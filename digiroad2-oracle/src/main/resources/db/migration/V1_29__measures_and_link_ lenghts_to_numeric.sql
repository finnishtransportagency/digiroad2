ALTER TABLE lrm_position ALTER COLUMN start_measure TYPE NUMERIC(1000, 3);
ALTER TABLE lrm_position ALTER COLUMN end_measure TYPE NUMERIC(1000, 3);

ALTER TABLE lrm_position_history ALTER COLUMN start_measure TYPE NUMERIC(1000, 3);
ALTER TABLE lrm_position_history ALTER COLUMN end_measure TYPE NUMERIC(1000, 3);

ALTER TABLE lane_position ALTER COLUMN start_measure TYPE NUMERIC(1000, 3);
ALTER TABLE lane_position ALTER COLUMN end_measure TYPE NUMERIC(1000, 3);

ALTER TABLE lane_history_position ALTER COLUMN start_measure TYPE NUMERIC(1000, 3);
ALTER TABLE lane_history_position ALTER COLUMN end_measure TYPE NUMERIC(1000, 3);

ALTER TABLE kgv_roadlink ALTER COLUMN geometrylength TYPE NUMERIC(1000, 3);

ALTER TABLE qgis_roadlinkex ALTER COLUMN horizontallength TYPE NUMERIC(1000, 3);