drop table temp_id;
create global temporary table temp_id (
  id number primary key
) on commit delete rows;
