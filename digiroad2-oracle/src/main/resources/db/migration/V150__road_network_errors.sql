CREATE TABLE road_network_errors
  (
    id              NUMERIC NOT NULL PRIMARY KEY,
    road_address_id NUMERIC NOT NULL,
    CONSTRAINT fk_road_address_error FOREIGN KEY (road_address_id) REFERENCES road_address (id)
  );
CREATE TABLE published_road_network
  (
    id      NUMERIC NOT NULL PRIMARY KEY,
    created TIMESTAMP NOT NULL
  );
CREATE TABLE published_road_address
  (
    network_id      NUMERIC NOT NULL,
    road_address_id NUMERIC NOT NULL,
    CONSTRAINT pk_published_road_address PRIMARY KEY (network_id, road_address_id),
    CONSTRAINT fk_network_id FOREIGN KEY (network_id) REFERENCES published_road_network (id),
    CONSTRAINT fk_published_road_address_id FOREIGN KEY (road_address_id) REFERENCES road_address (id)
  );