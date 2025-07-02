CREATE TABLE editing_restrictions (
    id INT PRIMARY KEY,
    municipality_id INT UNIQUE NOT NULL,
    state_road_restricted_asset_types VARCHAR(1000),
    municipality_road_restricted_asset_types VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_municipality_restriction
    FOREIGN KEY (municipality_id)
    REFERENCES municipality(id)
    ON DELETE CASCADE
);