ALTER TABLE roadlink DROP CONSTRAINT roadlink_pkey
ALTER TABLE roadlink ALTER COLUMN linkid DROP NOT NULL

ALTER TABLE unknown_speed_limit ALTER COLUMN link_id DROP NOT NULL
ALTER TABLE unknown_speed_limit DROP CONSTRAINT unknown_speed_limit_pkey