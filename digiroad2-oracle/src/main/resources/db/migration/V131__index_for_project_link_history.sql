--Add new column to show link-id of connecting link
CREATE INDEX plink_historic_lrm_idx ON PROJECT_LINK_HISTORY(lrm_position_id);