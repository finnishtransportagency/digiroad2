CREATE TABLE manouvre_samuutus_work_list
(
    id             bigint NOT NULL,
    assetId        numeric(38),
    linkIds        varchar(400),
);

alter table manouvre_samuutus_work_list add primary key (id);