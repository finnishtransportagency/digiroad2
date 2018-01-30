CREATE TABLE ROAD_Names (
  id Number Not Null,
  Road_Number NUMBER Not Null,
  Road_Name Varchar(255) Not Null,
  start_date DATE Not NULL,
  end_date DATE,
  valid_from DATE Not Null,
  valid_to DATE,
  created_By varchar(32) Not Null,
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


