CREATE TABLE manouvre_samuutus_work_list(
    assetId        numeric(38),
    linkIds        varchar(400)
);

ALTER TABLE manouvre_samuutus_work_list ADD CONSTRAINT manouvre_samuutus_work_list_asset_unique UNIQUE(assetId);