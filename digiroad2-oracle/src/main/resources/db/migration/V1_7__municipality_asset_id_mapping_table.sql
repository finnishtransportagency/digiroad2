CREATE TABLE municipality_asset_id_mapping
(
    asset_id    bigint NOT NULL,
    municipality_asset_id   varchar(128) NOT NULL,
    municipality_code   bigint NOT NULL,
    CONSTRAINT fk_municipality_asset_id_mapping_asset FOREIGN KEY (asset_id) REFERENCES asset(id) ON DELETE NO ACTION NOT DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT asset_id_unique UNIQUE (asset_id),
    CONSTRAINT pk_municipality_asset_id_code_pair PRIMARY KEY (municipality_asset_id, municipality_code)
);