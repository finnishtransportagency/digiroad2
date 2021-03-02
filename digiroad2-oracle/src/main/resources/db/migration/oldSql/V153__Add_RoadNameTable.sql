CREATE SEQUENCE road_name_seq  START WITH 1 INCREMENT BY 1 CACHE 10 NOCYCLE;

CREATE TABLE ROAD_Names (
  id Number PRIMARY KEY,
  Road_Number NUMBER Not Null,
  Road_Name Varchar(255) Not Null,
  start_date DATE Not NULL,
  end_date DATE,
  valid_from DATE Not Null,
  valid_to DATE,
  created_By varchar(32) Not Null,
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE TRIGGER roadname_seq_trigger
BEFORE INSERT
ON Road_Names
FOR EACH ROW
BEGIN
:NEW.ID:=road_name_seq.NEXTVAL;
END;

