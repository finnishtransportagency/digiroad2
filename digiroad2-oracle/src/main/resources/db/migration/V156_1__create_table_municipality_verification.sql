create table municipality_verification (
  id NUMBER NOT NULL,
  municipality_id REFERENCES municipality,
  asset_type_id NUMBER REFERENCES asset_type,
  verified_date TIMESTAMP default null,
	verified_by VARCHAR2(128) default null,
  valid_to TIMESTAMP default null,
  modified_by VARCHAR2(128) default null,
	PRIMARY KEY ("ID")
);