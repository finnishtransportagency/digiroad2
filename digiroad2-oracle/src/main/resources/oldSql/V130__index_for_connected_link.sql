--Add new column to show link-id of connecting link
CREATE INDEX project_link_connect_idx ON PROJECT_LINK(connected_link_id);