ALTER TABLE PROJECT_RESERVED_ROAD_PART
   ADD CONSTRAINT "INDEX ROAD_PART_UNIQ" UNIQUE ("ROAD_NUMBER", "ROAD_PART_NUMBER");
-- this is needed to prevent multiple projects reserving the same road part. Other unique index remains as it is needed by project_link table
