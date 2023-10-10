CREATE TABLE assets_on_expired_road_links (
    asset_id BIGINT NOT NULL,
    asset_type_id INT NOT NULL,
    link_id varchar(40) NOT NULL,
    side_code INT NULL,
    start_measure NUMERIC(1000, 3) NULL,
    end_measure NUMERIC(1000, 3) NULL,
    road_link_expired_date TIMESTAMP NOT NULL,
    asset_geometry geometry NULL
);