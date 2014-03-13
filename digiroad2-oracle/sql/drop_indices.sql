declare
index_not_exists EXCEPTION;
PRAGMA EXCEPTION_INIT(index_not_exists, -1418);
begin
    execute immediate 'drop index road_node_sx';
exception
    when index_not_exists then null;
end;

declare
index_not_exists EXCEPTION;
PRAGMA EXCEPTION_INIT(index_not_exists, -1418);
begin
    execute immediate 'drop index road_link_sx';
exception
    when index_not_exists then null;
end;

declare
index_not_exists EXCEPTION;
PRAGMA EXCEPTION_INIT(index_not_exists, -1418);
begin
    execute immediate 'drop index text_property_sx';
exception
    when index_not_exists then null;
end;

declare
index_not_exists EXCEPTION;
PRAGMA EXCEPTION_INIT(index_not_exists, -1418);
begin
    execute immediate 'drop index multiple_choice_value_sx';
exception
    when index_not_exists then null;
end;
