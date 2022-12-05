CREATE TABLE IF NOT EXISTS change_table (
    id 						numeric(38,0) primary key,
    edit_date	            timestamp not null,
    edit_by	                varchar not null,
    change_type	            varchar not null,
    asset_type_id	        int4,
    asset_id	            numeric not null,
    asset_geometry	        geometry,
    start_m_value	        numeric,
    end_m_value	            numeric,
    value	                varchar,
    value_type	            varchar not null,
    link_id	                varchar,
    link_geometry	        geometry(linestringzm,3067),
    link_type	            int4,
    link_length	            numeric,
    link_functional_class	int4
);