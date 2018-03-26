ALTER TABLE TRAFFIC_DIRECTION ADD link_type NUMBER(9,0);

ALTER TABLE traffic_direction ADD modified_date timestamp default current_timestamp NOT NULL;