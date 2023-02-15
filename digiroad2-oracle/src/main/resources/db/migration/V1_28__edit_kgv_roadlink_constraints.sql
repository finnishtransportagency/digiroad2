ALTER TABLE kgv_roadlink DROP CONSTRAINT kgv_roadlink_linkid;
ALTER TABLE kgv_roadlink ADD CONSTRAINT kgv_roadlink_linkid UNIQUE (linkid);

ALTER TABLE kgv_roadlink DROP CONSTRAINT kgv_roadlink_mtkid;