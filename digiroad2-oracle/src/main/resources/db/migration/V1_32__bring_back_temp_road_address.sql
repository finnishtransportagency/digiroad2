CREATE TABLE IF NOT EXISTS temp_road_address_info(
    id                bigint NOT NULL,
    link_id           varchar(40) NOT NULL,
    municipality_code bigint NOT NULL,
    road_number       bigint NOT NULL,
    road_part         bigint NOT NULL,
    track_code        bigint NOT NULL,
    start_address_m   bigint NOT NULL,
    end_address_m     bigint NOT NULL,
    start_m_value     NUMERIC(1000, 3) NOT NULL,
    end_m_value       NUMERIC(1000, 3) NOT NULL,
    side_code         numeric(38),
    created_date      timestamp NOT NULL DEFAULT LOCALTIMESTAMP,
    created_by        varchar(128)
);