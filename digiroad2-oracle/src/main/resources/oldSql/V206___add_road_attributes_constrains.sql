ALTER TABLE road_link_attributes ADD CONSTRAINT active_row Unique (link_id, name, valid_to);

ALTER TABLE road_link_attributes ADD MML_ID NUMBER(10,0) DEFAULT NULL;