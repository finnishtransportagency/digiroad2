ALTER TABLE roadlink DROP CONSTRAINT roadlink_pkey;
ALTER TABLE roadlink ALTER COLUMN linkid DROP NOT NULL;

ALTER TABLE unknown_speed_limit DROP CONSTRAINT unknown_speed_limit_pkey;
ALTER TABLE unknown_speed_limit ALTER COLUMN link_id DROP NOT NULL;
CREATE INDEX unknown_speed_limit_link_id_idx ON unknown_speed_limit (link_id);