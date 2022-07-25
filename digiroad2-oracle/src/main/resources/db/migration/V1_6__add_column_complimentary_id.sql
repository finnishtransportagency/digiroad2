
ALTER TABLE roadlinkex ADD COLUMN linkid_UUID TYPE VARCHAR(40);
create index linkid_UUID_index on roadlinkex (linkid_UUID);

ALTER TABLE roadlinkex DROP CONSTRAINT roadlinkex_pkey;
alter table roadlinkex ADD primary key (linkid_UUID);