alter table lrm_position add adjusted_timestamp number(38, 0) default 0 not null;
alter table lrm_position add modified_date timestamp default current_timestamp not null;
UPDATE lrm_position SET modified_date = (SELECT MAX(COALESCE(modified_date, created_date)) FROM ASSET join ASSET_LINK ON ASSET.ID = ASSET_LINK.ASSET_ID WHERE ASSET_LINK.POSITION_ID = lrm_position.id);
