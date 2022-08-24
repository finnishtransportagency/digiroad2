
ALTER TABLE administrative_class ADD COLUMN vvh_id NUMERIC(38);
ALTER TABLE administrative_class ALTER COLUMN link_id TYPE VARCHAR(40);

ALTER TABLE functional_class ADD COLUMN vvh_id NUMERIC(38);
ALTER TABLE functional_class ALTER COLUMN link_id TYPE VARCHAR(40);

ALTER TABLE inaccurate_asset ADD COLUMN vvh_id NUMERIC(38);
ALTER TABLE inaccurate_asset ALTER COLUMN link_id TYPE VARCHAR(40);

ALTER TABLE incomplete_link ADD COLUMN vvh_id NUMERIC(38);
ALTER TABLE incomplete_link ALTER COLUMN link_id TYPE VARCHAR(40);

ALTER TABLE lane_history_position ADD COLUMN vvh_id BIGINT;
ALTER TABLE lane_history_position ALTER COLUMN link_id TYPE VARCHAR(40);

ALTER TABLE lane_position ADD COLUMN vvh_id BIGINT;
ALTER TABLE lane_position ALTER COLUMN link_id TYPE VARCHAR(40);

ALTER TABLE link_type ADD COLUMN vvh_id NUMERIC(38);
ALTER TABLE link_type ALTER COLUMN link_id TYPE VARCHAR(40);

ALTER TABLE lrm_position ADD COLUMN vvh_id NUMERIC(38);
ALTER TABLE lrm_position ALTER COLUMN link_id TYPE VARCHAR(40);

ALTER TABLE lrm_position_history ADD COLUMN vvh_id NUMERIC(38);
ALTER TABLE lrm_position_history ALTER COLUMN link_id TYPE VARCHAR(40);

ALTER TABLE manoeuvre_element ADD COLUMN vvh_id NUMERIC(38);
ALTER TABLE manoeuvre_element ALTER COLUMN link_id TYPE VARCHAR(40);

ALTER TABLE manoeuvre_element ADD COLUMN dest_vvh_id NUMERIC(38);
ALTER TABLE manoeuvre_element ALTER COLUMN dest_link_id TYPE VARCHAR(40);

ALTER TABLE manoeuvre_element_history ADD COLUMN vvh_id NUMERIC(38);
ALTER TABLE manoeuvre_element_history ALTER COLUMN link_id TYPE VARCHAR(40);

ALTER TABLE manoeuvre_element_history ADD COLUMN dest_vvh_id NUMERIC(38);
ALTER TABLE manoeuvre_element_history ALTER COLUMN dest_link_id TYPE VARCHAR(40);

ALTER TABLE road_link_attributes ADD COLUMN vvh_id NUMERIC(38);
ALTER TABLE road_link_attributes ALTER COLUMN link_id TYPE VARCHAR(40);

ALTER TABLE traffic_direction ADD COLUMN vvh_id NUMERIC(38);
ALTER TABLE traffic_direction ALTER COLUMN link_id TYPE VARCHAR(40);

ALTER TABLE unknown_speed_limit ADD COLUMN vvh_id NUMERIC(38);
ALTER TABLE unknown_speed_limit ALTER COLUMN link_id TYPE VARCHAR(40);
