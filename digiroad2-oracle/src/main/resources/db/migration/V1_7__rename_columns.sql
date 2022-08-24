
ALTER TABLE roadlink RENAME COLUMN kmtkid to vvh_id;
ALTER TABLE roadlink ALTER COLUMN vvh_id TYPE NUMERIC(38) USING vvh_id::numeric(38,0);
ALTER TABLE roadlink ALTER COLUMN linkid TYPE VARCHAR(40);

create index vvh_id on roadlink (vvh_id);