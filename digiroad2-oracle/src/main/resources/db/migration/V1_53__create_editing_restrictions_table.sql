CREATE TABLE editing_restrictions (
    id INT PRIMARY KEY DEFAULT nextval('primary_key_seq'),
    municipality_id INT UNIQUE NOT NULL,
    state_road_restricted_asset_types VARCHAR(1000),
    municipality_road_restricted_asset_types VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);