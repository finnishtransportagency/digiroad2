truncate table unknown_speed_limit;
alter table unknown_speed_limit drop column mml_id;
alter table unknown_speed_limit add primary key (link_id);