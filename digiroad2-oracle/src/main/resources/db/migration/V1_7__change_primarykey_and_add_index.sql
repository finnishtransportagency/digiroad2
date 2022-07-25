create index kmtkid_index on roadlink (kmtkid);

ALTER TABLE roadlink DROP CONSTRAINT roadlink_pkey;
alter table roadlink ADD primary key (kmtkid);