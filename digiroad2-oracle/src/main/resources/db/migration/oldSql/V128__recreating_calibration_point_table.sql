Drop Table CALIBRATION_POINT;

Create Table CALIBRATION_POINT (
	ID Number Not Null,
	PROJECT_LINK_ID Number Not Null,
	PROJECT_ID Number Not Null,
	LINK_M Number(8,3) Not Null,
	ADDRESS_M Number Not Null,
	Constraint CALIBRATION_POINT_PK Primary Key (
		ID ) Enable,
	Constraint CALIBRATION_POINT_FK1 Foreign KEY (
		PROJECT_LINK_ID
	) References PROJECT_LINK (
		ID
	) On Delete Cascade Enable,
	Constraint CALIBRATION_POINT_FK3 Foreign Key (
		PROJECT_ID
	) References PROJECT (
		ID
	) On Delete Cascade Enable
);

Create Sequence CALIBRATION_POINT_ID_SEQ Increment By 1 Start With 1 MaxValue 9999999999 MinValue 1 Cache 20;

Create Unique Index CALIBRATION_POINTS_FK_UNIQUE on CALIBRATION_POINT (ID, PROJECT_LINK_ID, PROJECT_ID);