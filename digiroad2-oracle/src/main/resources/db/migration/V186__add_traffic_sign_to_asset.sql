alter table asset
add traffic_sign_id number constraint fk_traffic_sign_id references asset(id);