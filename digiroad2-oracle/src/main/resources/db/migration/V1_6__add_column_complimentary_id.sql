
ALTER TABLE roadlinkex ADD COLUMN linkid_UUID TYPE VARCHAR(40);
create index linkid_UUID_index on roadlinkex (linkid_UUID);