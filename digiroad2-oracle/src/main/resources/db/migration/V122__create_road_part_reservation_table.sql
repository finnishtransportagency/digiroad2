CREATE TABLE "DR2DEV4"."PROJECT_RESERVED_ROAD_PART"
(	"ID" NUMBER,
	"ROAD_NUMBER" NUMBER,
	"ROAD_PART_NUMBER" NUMBER,
	"PROJECT_ID" NUMBER NOT NULL ENABLE,
	"CREATED_BY" VARCHAR2(128 BYTE),
	"ROAD_LENGTH" NUMBER,
  "DISCONTINUITY" NUMBER,
  "ELY" NUMBER,
	 PRIMARY KEY ("ID"),
	 CONSTRAINT "COMBINED" UNIQUE ("PROJECT_ID", "ROAD_NUMBER", "ROAD_PART_NUMBER"),
	 FOREIGN KEY ("PROJECT_ID")
	  REFERENCES "DR2DEV4"."PROJECT" ("ID") ENABLE
   );

INSERT INTO PROJECT_RESERVED_ROAD_PART(id, road_number, road_part_number, project_id, created_by)
  (SELECT viite_general_seq.nextval, road_number, road_part_number, project_id, created_by
  from (SELECT pl.road_number, pl.road_part_number, project_id, MAX(p.CREATED_BY) as created_by FROM PROJECT p JOIN
PROJECT_LINK pl ON (p.id = pl.project_id) WHERE p.STATE IN (1,2,4,99) group by road_number, road_part_number, project_id) foo);

ALTER TABLE PROJECT_LINK
MODIFY (road_number NUMBER CONSTRAINT road_not_null NOT NULL);

ALTER TABLE PROJECT_LINK
MODIFY (road_part_number NUMBER CONSTRAINT part_not_null NOT NULL);

ALTER TABLE PROJECT_LINK
ADD CONSTRAINT fk_link_reserved
   FOREIGN KEY (project_id, road_number, road_part_number)
   REFERENCES PROJECT_RESERVED_ROAD_PART(project_id, road_number, road_part_number);

