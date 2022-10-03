CREATE TABLE IF NOT EXISTS qgis_roadlinkex (
    vvh_id numeric(38) NULL,
    linkid varchar(40) NULL,
    sourceinfo int4 NULL,
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
    geometrylength double precision NULL,
    created_date timestamp NULL,
    created_user varchar(64) NULL,
    last_edited_date timestamp NULL,
    shape geometry NULL,
    track_code int4 NULL,
    cust_owner int4 NULL,
    CONSTRAINT qgis_roadlinkex_linkid UNIQUE (linkid) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX IF NOT EXISTS qgis_roadlinkex_roadlink_spatial_index ON qgis_roadlinkex USING gist (shape);
CREATE INDEX IF NOT EXISTS qgis_roadlinkex_adminclass_index ON qgis_roadlinkex USING btree (adminclass);
CREATE INDEX IF NOT EXISTS qgis_roadlinkex_municipality_index ON qgis_roadlinkex USING btree (municipalitycode);