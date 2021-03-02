create global temporary table ROAD_LINK_LOOKUP (ID NUMBER) on commit delete rows;

CREATE UNIQUE INDEX ROAD_LINK_LOOKUP_ID ON ROAD_LINK_LOOKUP ("ID");

