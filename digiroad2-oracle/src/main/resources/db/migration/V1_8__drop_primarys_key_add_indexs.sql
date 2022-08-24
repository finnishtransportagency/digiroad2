ALTER TABLE roadlink DROP CONSTRAINT roadlink_pkey;
ALTER TABLE roadlink ALTER COLUMN linkid DROP NOT NULL;

ALTER TABLE unknown_speed_limit DROP CONSTRAINT unknown_speed_limit_pkey;
ALTER TABLE unknown_speed_limit ALTER COLUMN link_id DROP NOT NULL;

CREATE INDEX unknown_speed_limit_link_id_idx ON unknown_speed_limit (link_id);
CREATE INDEX unknown_speed_limit_vvh_id_idx ON unknown_speed_limit (vvh_id);
CREATE INDEX lane_history_position_vvh_id_idx ON lane_history_position (vvh_id);
CREATE INDEX lane_position_vvh_id_idx ON lane_position (vvh_id);
CREATE INDEX lrm_position_vvh_id_idx ON lrm_position (vvh_id);
CREATE INDEX lrm_position_history_vvh_id_idx ON lrm_position_history (vvh_id);
CREATE INDEX road_link_attributest_vvh_id_idx ON road_link_attributes (vvh_id);
CREATE INDEX administrative_class_vvh_id_idx ON administrative_class (vvh_id);
CREATE INDEX traffic_direction_vvh_id_idx ON traffic_direction (vvh_id);
CREATE INDEX functional_class_vvh_id_idx ON functional_class (vvh_id);
CREATE INDEX incomplete_link_vvh_id_idx ON incomplete_link (vvh_id);
CREATE INDEX link_type_vvh_id_idx ON link_type (vvh_id);
CREATE INDEX roadlink_vvh_id_idx ON roadlink (vvh_id);
CREATE INDEX manoeuvre_element_history_vvh_id_idx ON manoeuvre_element_history (vvh_id);
CREATE INDEX manoeuvre_element_vvh_id_idx ON manoeuvre_element (vvh_id);

CREATE INDEX manoeuvre_element_history_dest_vvh_id_idx ON manoeuvre_element_history (dest_vvh_id);
CREATE INDEX manoeuvre_element_dest_vvh_id_idx ON manoeuvre_element (dest_vvh_id);



