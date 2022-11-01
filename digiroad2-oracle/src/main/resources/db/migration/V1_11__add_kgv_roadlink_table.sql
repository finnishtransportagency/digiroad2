CREATE TABLE IF NOT EXISTS kgv_roadlink (
    linkid varchar(40) NULL,
	vvh_id numeric(38) NULL,
	mtkid numeric(38) NULL,
	adminclass int4 NULL,
	municipalitycode int4 NULL,
	mtkclass numeric(38) NULL,
	roadname_fi varchar(80) NULL,
	roadname_se varchar(80) NULL,
	roadnamesme varchar(80) NULL,
	roadnamesmn varchar(80) NULL,
	roadnamesms varchar(80) NULL,
	roadnumber numeric(38) NULL,
	roadpartnumber int4 NULL,
	surfacetype int4 NULL,
	constructiontype int4 NULL,
	directiontype int4 NULL,
	verticallevel int4 NULL,
	horizontalaccuracy numeric NULL,
	verticalaccuracy numeric NULL,
	geometrylength numeric NULL,
	mtkhereflip int4 NULL,
	from_left numeric(38) NULL,
	to_left numeric(38) NULL,
	from_right numeric(38) NULL,
	to_right numeric(38) NULL,
	created_date timestamp NULL,
	last_edited_date timestamp NULL,
	shape geometry(linestringzm, 3067) NULL,
	CONSTRAINT kgv_roadlink_linkid UNIQUE (linkid) DEFERRABLE INITIALLY DEFERRED,
	CONSTRAINT kgv_roadlink_mtkid UNIQUE (mtkid) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX IF NOT EXISTS kgv_roadlink_adminclass_index ON kgv_roadlink USING btree (adminclass);
CREATE INDEX IF NOT EXISTS kgv_roadlink_constructio_index ON kgv_roadlink USING btree (constructiontype);
CREATE INDEX IF NOT EXISTS kgv_roadlink_linkid_index ON kgv_roadlink USING btree (linkid);
CREATE INDEX IF NOT EXISTS kgv_roadlink_linkid_mtkc_index ON kgv_roadlink USING btree (linkid, mtkclass);
CREATE INDEX IF NOT EXISTS kgv_roadlink_mtkclass_index ON kgv_roadlink USING btree (mtkclass);
CREATE INDEX IF NOT EXISTS kgv_roadlink_mtkid_index ON kgv_roadlink USING btree (mtkid);
CREATE INDEX IF NOT EXISTS kgv_roadlink_mtkid_mtkhereflip_index ON kgv_roadlink USING btree (mtkid, mtkhereflip);
CREATE INDEX IF NOT EXISTS kgv_roadlink_muni_mtkc_index ON kgv_roadlink USING btree (municipalitycode, mtkclass);
CREATE INDEX IF NOT EXISTS kgv_roadlink_municipality_index ON kgv_roadlink USING btree (municipalitycode);
CREATE INDEX IF NOT EXISTS kgv_roadlink_roadlink_spatial_index ON kgv_roadlink USING gist (shape);
CREATE INDEX IF NOT EXISTS kgv_roadlink_roadlink_vvh_id_idx ON kgv_roadlink USING btree (vvh_id);
CREATE INDEX IF NOT EXISTS kgv_roadlink_roadnum_mtkc_index ON kgv_roadlink USING btree (roadnumber, mtkclass);
CREATE INDEX IF NOT EXISTS kgv_roadlink_vvh_id ON kgv_roadlink USING btree (vvh_id);