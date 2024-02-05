CREATE TABLE replace_info
(
    id                  SERIAL PRIMARY KEY,
    old_link_id         varchar(40),
    old_geometry        geometry(linestringzm, 3067),
    new_link_id         varchar(40),
    new_geometry        geometry(linestringzm, 3067),
    old_from_m_value    numeric(1000, 3),
    old_to_m_value      numeric(1000, 3),
    new_from_m_value    numeric(1000, 3),
    new_to_m_value      numeric(1000, 3),
    digitization_change BOOLEAN
);
