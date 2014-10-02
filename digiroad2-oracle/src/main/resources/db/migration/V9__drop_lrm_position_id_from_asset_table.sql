insert into asset_link select id, lrm_position_id from asset;

alter table asset drop column lrm_position_id;