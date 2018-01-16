CREATE TABLE road_network_errors
  (
    id              NUMERIC NOT NULL PRIMARY KEY,
    road_address_id NUMERIC NOT NULL,
    error_code      NUMBER NOT NULL,
    CONSTRAINT fk_road_address_error FOREIGN KEY (road_address_id) REFERENCES road_address (id)
  );
CREATE TABLE published_road_network
  (
    id      NUMERIC NOT NULL PRIMARY KEY,
    created TIMESTAMP NOT NULL,
    valid_to TIMESTAMP
  );
CREATE TABLE published_road_address
  (
    network_id      NUMERIC NOT NULL,
    road_address_id NUMERIC NOT NULL,
    CONSTRAINT pk_published_road_address PRIMARY KEY (network_id, road_address_id),
    CONSTRAINT fk_network_id FOREIGN KEY (network_id) REFERENCES published_road_network (id),
    CONSTRAINT fk_published_road_address_id FOREIGN KEY (road_address_id) REFERENCES road_address (id)
  );

create sequence road_network_errors_key_seq
  minvalue 1
  maxvalue 999999999999999999999999999
  start with 1
  increment by 1
  cache 100
  cycle;

create sequence published_road_network_key_seq
  minvalue 1
  maxvalue 999999999999999999999999999
  start with 1
  increment by 1
  cache 100
  cycle;