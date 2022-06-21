create table roadlink (
    kmtkid varchar(40),
	objectid numeric(38,0),
	mtkid numeric(38,0),
	drid numeric(38,0),
	linkid numeric(38,0),
	sourceinfo integer,
	adminclass integer,
	municipalitycode integer,
	mtkgroup integer,
	mtkclass numeric(38,0),
	roadname_fi varchar(80),
	roadname_se varchar(80),
	roadname_sm varchar(80),
	roadnumber numeric(38,0),
	roadpartnumber integer,
	surfacetype integer,
	constructiontype integer,
	directiontype integer,
	verticallevel integer,
	horizontalaccuracy numeric(38,0),
	verticalaccuracy numeric(38,0),
	vectortype numeric(5,0),
	geometrylength numeric(11,3),
	minanleft numeric(38,0),
	maxanleft numeric(38,0),
	minanright numeric(38,0),
	maxanright numeric(38,0),
	validfrom timestamp,
	created_date timestamp,
	created_user varchar(64),
	last_edited_date timestamp,
	geometry_edited_date timestamp,
	validationstatus integer,
	updatenumber integer,
	objectstatus integer,
	subtype integer,
	shape geometry(linestringzm,3067),
	se_anno_cad_data bytea,
	mtkhereflip integer,
	from_left numeric(38,0),
	to_left numeric(38,0),
	from_right numeric(38,0),
	to_right numeric(38,0),
	startnode numeric(38,0),
	endnode numeric(38,0),
    constraint roadlink_linkid unique (linkid) deferrable initially deferred,
	constraint roadlink_mtkid unique (mtkid) deferrable initially deferred
);
alter table roadlink add primary key (linkid);

create index roadlink_spatial_index on roadlink using gist (shape);
create index adminclass_index on roadlink (adminclass);
create index constructio_index on roadlink (constructiontype);
create index linkid_index on roadlink (linkid);
create index mtkclass_index on roadlink (mtkclass);
create index mtkid_index on roadlink (mtkid);
create index municipality_index on roadlink (municipalitycode);
create index updatenumbe_index on roadlink (updatenumber);
create index updatenumber_adminclass_municipalitycode_index on roadlink (updatenumber,adminclass,municipalitycode);
create index updatenumbe_adminclass_index on roadlink (updatenumber,adminclass);
create index mtkid_mtkhereflip_index on roadlink (mtkid,mtkhereflip);
create index linkid_mtkc_index on roadlink (linkid,mtkclass);
create index muni_mtkc_index on roadlink (municipalitycode,mtkclass);
create index roadnum_mtkc_index on roadlink (roadnumber,mtkclass);
create index endnode_municipalitycode_index on roadlink (endnode,municipalitycode);
create index startnode_municipalitycode_index on roadlink (startnode,municipalitycode);

create table roadlinkex (
	objectid numeric(38,0),
	drid numeric(38,0),
	linkid numeric(38,0),
	sourceinfo integer,
	adminclass integer,
	municipalitycode integer,
	mtkgroup integer,
	mtkclass numeric(38,0),
	roadname_fi varchar(80),
	roadname_se varchar(80),
	roadname_sm varchar(80),
	roadnumber numeric(38,0),
	roadpartnumber integer,
	surfacetype integer,
	constructiontype integer,
	directiontype integer,
	verticallevel integer,
	horizontalaccuracy numeric(38,0),
	verticalaccuracy numeric(38,0),
	vectortype integer,
	geometrylength double precision,
	minanleft numeric(38,0),
	maxanleft numeric(38,0),
	minanright numeric(38,0),
	maxanright numeric(38,0),
	validfrom timestamp,
	created_date timestamp,
	created_user varchar(64),
	last_edited_date timestamp,
	geometry_edited_date timestamp,
	validationstatus integer,
	feedbackstatus integer,
	feedbackinfo varchar(255),
	objectstatus integer,
	subtype integer,
	jobid numeric(38,0),
	shape geometry(linestringzm,3067),
	se_anno_cad_data bytea,
	from_left numeric(38,0),
	to_left numeric(38,0),
	from_right numeric(38,0),
	to_right numeric(38,0),
	estimated_completion timestamp,
	track_code integer,
	cust_class varchar(50),
	cust_id_str varchar(50),
	cust_id_num numeric(38,0),
	cust_owner numeric(38,0),
	constraint roadlinkex_linkid unique (linkid) deferrable initially deferred
);

alter table roadlinkex add primary key (linkid);

create index roadlinkex_spatial_index on roadlinkex using gist(shape);
create index roadlinkex_adminclass_index on roadlinkex (adminclass);
create index roadlinkex_jobid_index on roadlinkex (jobid);
create index roadlinkex_municipalitycode_index on roadlinkex (municipalitycode);
