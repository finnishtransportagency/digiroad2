alter table manoeuvre
add traffic_sign_id number constraint fk_traffic_sign_id references asset(id);